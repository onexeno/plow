"""Commonly used Job widgets."""

from functools import partial 
from itertools import chain

import plow.client
import plow.gui.constants as constants

from plow.gui.manifest import QtCore, QtGui
from plow.gui.form import FormWidget, FormWidgetFactory
from plow.gui.util import ask
from plow.gui.common.widgets import FilterableListBox



class JobProgressFormWidget(FormWidget):
    def __init__(self, value, parent=None):
        FormWidget.__init__(self, parent)
        self.setWidget(JobProgressBar(value, parent))

FormWidgetFactory.register("jobProgressBar", JobProgressFormWidget)


class JobStateFormWidget(FormWidget):
    def __init__(self, value, parent=None):
        FormWidget.__init__(self, parent)
        self.setWidget(JobStateWidget(value, False, parent))
        self._widget.setMinimumWidth(100)

FormWidgetFactory.register("jobState", JobStateFormWidget)


class JobProgressBar(QtGui.QWidget):
    # Left, top, right, bottom
    __PEN = QtGui.QColor(33, 33, 33)

    Margins = [5, 2, 10, 4]

    def __init__(self, totals, parent=None):
        QtGui.QWidget.__init__(self, parent)
        self.setTotals(totals)
        self.setSizePolicy(QtGui.QSizePolicy.Expanding,
            QtGui.QSizePolicy.Preferred)

        ## Missing ability to detect size
    
    def setTotals(self, totals):
        self.__totals = totals
        self.__values =  [
            totals.waiting,
            totals.running,
            totals.dead, 
            totals.eaten,
            totals.depend,
            totals.succeeded
        ]
        self.update()

    def paintEvent(self, event):

        total_width = self.width() - self.Margins[2]
        total_height = self.height() - self.Margins[3]
        total_tasks = float(self.__totals.total)

        bar = []
        for i, v in enumerate(self.__values):
            if v == 0:
                continue
            bar.append((total_width * (v / total_tasks), constants.COLOR_TASK_STATE[i + 1]))

        painter = QtGui.QPainter()
        painter.begin(self)
        painter.setRenderHints(
            painter.HighQualityAntialiasing |
            painter.SmoothPixmapTransform |
            painter.Antialiasing)
        painter.setPen(self.__PEN)

        move = 0
        for width, color in bar:
            painter.setBrush(color)
            rect = QtCore.QRectF(
                self.Margins[0],
                self.Margins[1],
                total_width,
                total_height)
            if move:
                rect.setLeft(move)
            move+=width
            painter.drawRoundedRect(rect, 3, 3)
        painter.end()
        event.accept()


class JobColumnWidget(QtGui.QScrollArea):

    DATA_ROLE = FilterableListBox.DATA_ROLE

    selectionChanged = QtCore.Signal(list)

    def __init__(self, parent=None):
        super(JobColumnWidget, self).__init__(parent)

        self.__currentJob = None
        self.__currentLayer = None 
        self.__currentTask = None

        self.setFocusPolicy(QtCore.Qt.NoFocus)

        contentWidget = QtGui.QWidget(self)
        self.setWidget(contentWidget)
        self.setWidgetResizable(True)

        mainLayout = QtGui.QHBoxLayout(contentWidget)

        self._jobWidget = job = JobSelectionWidget(self)
        job.setMinimumWidth(220)

        self._layerWidget = layer = FilterableListBox(parent=self)
        layer.setLabel("Layer:")
        layer.setMinimumWidth(180)

        self._taskWidget = task = FilterableListBox(parent=self)
        task.setLabel("Task:")
        task.setMinimumWidth(120)

        mainLayout.addWidget(job)
        mainLayout.addWidget(layer)
        mainLayout.addWidget(task)

        # connections
        job.selectionChanged.connect(self._jobSelectionChanged)
        job.valueClicked.connect(layer.clearSelection)
        job.valueClicked.connect(task.clearSelection)

        layer.selectionChanged.connect(self._layerSelectionChanged)
        layer.valueClicked.connect(task.clearSelection)

        task.selectionChanged.connect(self._taskSelectionChanged)

    @property 
    def currentJob(self):
        return self.__currentJob 

    @property 
    def currentLayer(self):
        return self.__currentLayer

    def reset(self):
        self._clearTask()
        self._clearLayer()
        self._clearJob()

    def setJobFilter(self, val):
        self._jobWidget.setFilter(val, selectFirst=True)

    def setLayerFilter(self, val):
        self._layerWidget.setFilter(val, selectFirst=True)

    def setTaskFilter(self, val):
        self._taskWidget.setFilter(val, selectFirst=True)

    def setSingleSelections(self, enabled):
        for w in (self._jobWidget, self._layerWidget, self._taskWidget):
            w.setSingleSelections(enabled)

    def getSelection(self):
        for w in (self._taskWidget, self._layerWidget, self._jobWidget):
            items = w.getSelectedValues(self.DATA_ROLE)
            if items:
                return items

        return []

    def setLayersEnabled(self, enabled):
        self._layerWidget.clearSelection(clearFilter=False)
        self._layerWidget.setEnabled(enabled)
        self.setTasksEnabled(enabled)

    def setTasksEnabled(self, enabled):
        self._taskWidget.clearSelection(clearFilter=False)
        self._taskWidget.setEnabled(enabled)

    def _jobSelectionChanged(self, selection):
        self.__currentJob = None
        self._clearTask()

        count = len(selection)
        if count != 1:
            self._clearLayer()
            return

        jobs = self._jobWidget.getSelectedValues(self.DATA_ROLE)
        if not jobs:
            return

        self.__currentJob = jobs[0]

        if self._layerWidget.isEnabled():
            layers = self.__currentJob.get_layers()
            layerNames = [l.name for l in layers]
            self._layerWidget.setStringList(layerNames, data=layers)

        self.selectionChanged.emit(jobs)

    def _layerSelectionChanged(self, selection):
        self.__currentLayer = None

        count = len(selection)
        if count != 1:
            self._clearTask()
            return

        self.__currentLayer = None
        layers = self._layerWidget.getSelectedValues(self.DATA_ROLE)
        if not layers:
            return

        layer = layers[0]
        self.__currentLayer = layer

        if self._taskWidget.isEnabled():
            tasks = layer.get_tasks()
            self._taskWidget.setStringList([t.name for t in tasks], data=tasks)

        self.selectionChanged.emit(layers)

    def _taskSelectionChanged(self, selection):
        tasks = self._taskWidget.getSelectedValues(self.DATA_ROLE)
        self.selectionChanged.emit(tasks)

    def _clearLayer(self):
        self.__currentLayer = None 
        self._layerWidget.clear()

    def _clearTask(self):
        self.__currentTask = None
        self._taskWidget.clear()

    def _clearJob(self):
        self.__currentJob = None
        self._jobWidget.clearSelection()
        self._jobWidget.setFilter('')


class JobSelectionWidget(FilterableListBox):

    def __init__(self, parent=None, **kwargs):
        super(JobSelectionWidget, self).__init__(parent=parent)
        self.setLabel("Job:")

        if not kwargs:
            kwargs = {"states": [plow.JobState.RUNNING]}

        kwargs['matchingOnly'] = True
        self.__opts = kwargs

        self.refresh()

    def refresh(self):
        jobs = plow.client.get_jobs(**self.__opts)
        jobNames = [job.name for job in jobs]        
        self.setStringList(jobNames, data=jobs)


class JobSelectionDialog(QtGui.QDialog):

    def __init__(self, parent=None):
        QtGui.QDialog.__init__(self, parent)
        layout = QtGui.QVBoxLayout(self)
        self.__jobSelector = JobSelectionWidget(self)

        self.__btns = QtGui.QDialogButtonBox(
            QtGui.QDialogButtonBox.Ok | 
            QtGui.QDialogButtonBox.Cancel)

        layout.addWidget(self.__jobSelector)
        layout.addWidget(self.__btns)

        # connections
        self.__jobSelector.valueDoubleClicked.connect(self.accept)
        self.__btns.accepted.connect(self.accept)
        self.__btns.rejected.connect(self.reject)

    def getSelectedJobs(self):
        names = self.__jobSelector.getSelectedValues()
        jobs = plow.client.get_jobs(matchingOnly=True, 
                                    name=names, 
                                    states=[plow.JobState.RUNNING])

        return jobs


class JobStateWidget(QtGui.QWidget):
    """
    A widget for displaying the job state.
    """
    def __init__(self, state, hasErrors=False, parent=None):
        QtGui.QWidget.__init__(self, parent)
        self.__state = state
        self.__hasErrors = hasErrors
        self.setSizePolicy(QtGui.QSizePolicy.Minimum,
            QtGui.QSizePolicy.Preferred)

    def getState(self):
        return self.__state

    def hasErrors(self):
        return self.__hasErrors

    def setState(self, state, hasErrors):
        self.__state = state
        self.__hasErrors = hasErrors

    def paintEvent(self, event):

        total_width = self.width()
        total_height = self.height()

        painter = QtGui.QPainter()
        painter.begin(self)
        painter.setRenderHints(
            painter.HighQualityAntialiasing |
            painter.SmoothPixmapTransform |
            painter.Antialiasing)
        
        if self.__hasErrors:
            painter.setBrush(constants.RED)
        else:
            painter.setBrush(constants.COLOR_JOB_STATE[self.__state])
        
        painter.setPen(painter.brush().color().darker())

        rect = QtCore.QRect(0, 0, total_width, total_height)
        painter.drawRoundedRect(rect, 5, 5)
        painter.setPen(QtCore.Qt.black)
        painter.drawText(rect, QtCore.Qt.AlignCenter, constants.JOB_STATES[self.__state])
        painter.end()



def jobContextMenu(jobs, refreshCallback=None, parent=None):
    """
    Get a job context QMenu with common operations
    """
    menu = QtGui.QMenu(parent)

    if not isinstance(jobs, (tuple, set, list, dict)):
        jobs = [jobs]

    total = len(jobs)
    isPaused = jobs[0].paused

    pause = menu.addAction(QtGui.QIcon(":/images/pause.png"), "Un-Pause" if isPaused else "Pause")
    kill = menu.addAction(QtGui.QIcon(":/images/kill.png"), "Kill Job%s" % 's' if total else '')

    menu.addSeparator()

    kill_tasks = menu.addAction(QtGui.QIcon(":/images/kill.png"), "Kill Tasks")
    eat_tasks = menu.addAction(QtGui.QIcon(":/images/eat.png"), "Eat Dead Tasks")
    retry_tasks = menu.addAction(QtGui.QIcon(":/images/retry.png"), "Retry Dead Tasks")


    def action_on_tasks(fn, job_list, dead=False):
        if dead:
            states = [plow.client.TaskState.DEAD]
        else:
            states = []

        tasks = list(chain.from_iterable(j.get_tasks(states=states) for j in job_list))

        if not tasks:
            return

        msg = "Run %r on %d jobs  (%d tasks) ?" % (fn.__name__, len(job_list), len(tasks))
        if not ask(msg, parent=parent):
            return

        if tasks:
            fn(tasks=tasks)
            if refreshCallback:
                refreshCallback()  


    eat_tasks.triggered.connect(partial(action_on_tasks, 
                                        plow.client.eat_tasks, 
                                        jobs, 
                                        dead=True))

    retry_tasks.triggered.connect(partial(action_on_tasks, 
                                          plow.client.retry_tasks, 
                                          jobs, 
                                          dead=True))

    kill_tasks.triggered.connect(partial(action_on_tasks, 
                                         plow.client.kill_tasks, 
                                         jobs, 
                                         dead=False))

    def pause_fn(job_list, pause):
        for j in job_list:
            j.pause(pause)

        if refreshCallback:
            refreshCallback()

    pause.triggered.connect(partial(pause_fn, jobs, not isPaused))

    def kill_fn(job_list):
        if not ask("Kill %d job(s) ?" %  len(job_list), parent=parent):
            return

        for j in job_list:
            j.kill('plow-wrangler')

        if refreshCallback:
            refreshCallback()

    kill.triggered.connect(partial(kill_fn, jobs))

    return menu





