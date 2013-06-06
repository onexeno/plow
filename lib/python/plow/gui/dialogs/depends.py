
from plow.gui.manifest import QtCore, QtGui
from plow import client 
from plow.client import DependType
from plow.gui.common.job import JobColumnWidget 
from plow.gui.common.widgets import FilterableListBox 


DEPEND_TYPES = {}

def _init():
    for a in dir(DependType):
        if a.startswith('_'):
            continue
        val = getattr(DependType, a)
        DEPEND_TYPES[val] = a

_init()
del _init


class DependencyWizard(QtGui.QWizard):

    def __init__(self, *args, **kwargs):
        super(DependencyWizard, self).__init__(*args, **kwargs)
        self.setWindowTitle("Dependency Wizard")
        self.resize(650, self.height())
        self.__src = None

        self.addPage(ChooseTypeDepPage(self))
        self.addPage(ChooseTargetsPage(self))
        self.addPage(ConfirmApplyPage(self))

    def sourceObject(self):
        return self.__src

    def setSourceObject(self, obj):
        if not isinstance(obj, (client.Job, client.Layer, client.Task)):
            raise ValueError("Source object must be an instance of Job/Layer/Task")

        self.__src = obj
        self.restart()

    @property 
    def dependantObjects(self):
        return self.field("sourceSelection")

    @property 
    def dependsOnObjects(self):
        return self.field("destSelection")

    def accept(self):
        print self.dependantObjects, " =>", self.dependsOnObjects
        super(DependencyWizard, self).accept()


class BaseDepPage(QtGui.QWizardPage):

    def validatePage(self):
        wiz = self.wizard()
        if not wiz:
            return False

        if not wiz.sourceObject():
            return False 

        return super(BaseDepPage, self).validatePage()

    def sourceObject(self):
        wiz = self.wizard()
        if not wiz:
            return None 

        return wiz.sourceObject()


class ChooseTypeDepPage(BaseDepPage):

    def __init__(self, *args, **kwargs):
        super(ChooseTypeDepPage, self).__init__(*args, **kwargs)    

        self.setTitle("Choose the type of dependency")

        layout = QtGui.QVBoxLayout(self)
        self.__title = QtGui.QLabel("", self)
        layout.addWidget(self.__title)

        self.__radioGroup = QtGui.QButtonGroup(self)
        self.__radioGroup.setExclusive(True)
        for val, name in sorted(DEPEND_TYPES.items(), key=lambda i: i[1]):
            name = name.replace("_", " ").title()
            btn = QtGui.QRadioButton(name, self)
            self.__radioGroup.addButton(btn, val)
            layout.addWidget(btn)

        self.registerField("dependType*", self, "dependType")
        self.__radioGroup.buttonClicked[int].connect(self.completeChanged)

    def initializePage(self):
        src = self.sourceObject()
        typ = src.__class__.__name__.lower() if src else None

        buttons = self.__radioGroup.buttons()
        defaultSelection = False
        for button in buttons:
            text = button.text().lower()
            enabled = bool(src) and text.startswith(typ)
            button.setEnabled(enabled)
            if enabled and not defaultSelection:
                button.setChecked(True)
                defaultSelection = True
            else:
                button.setChecked(False)

            button.setEnabled(bool(src) and text.startswith(typ))
            button.setChecked(False)

        if not src:
            msg = "<font color='red'>No Plow object given to apply dependencies</font>"
            self.__title.setText(msg)
            return

        name = src.name 
        txt = "Dependency Options for <strong>%s</strong> %r" % (typ.title(), name)
        self.__title.setText(txt)

    def isComplete(self):
        if self.dependType == -1:
            return False 

        return super(ChooseTypeDepPage, self).isComplete()

    def getDependType(self):
        return self.__radioGroup.checkedId()

    dependType = QtCore.Property(int, fget=getDependType)


class ChooseTargetsPage(BaseDepPage):

    def __init__(self, *args, **kwargs):
        super(ChooseTargetsPage, self).__init__(*args, **kwargs)

        self.setTitle("Apply the dependency")
        self.setSubTitle("Choose first the item that depends on others.\n" \
                         "Then choose one or more items to depend on.") 

        layout = QtGui.QVBoxLayout(self)
        layout.setContentsMargins(2, 0, 2, 0)

        self.__errText = QtGui.QLabel("", self)

        group1 = QtGui.QGroupBox("Dependant Item", self)
        groupLayout1 = QtGui.QVBoxLayout(group1)
        groupLayout1.setContentsMargins(0, 0, 0, 0)
        self.__sourceSelector = src = JobColumnWidget(self)
        src.setSingleSelections(True)
        groupLayout1.addWidget(src)

        group2 = QtGui.QGroupBox("Item Depends On", self)
        groupLayout2 = QtGui.QVBoxLayout(group2)
        groupLayout2.setContentsMargins(0, 0, 0, 0)
        self.__destSelector = dst = JobColumnWidget(self)
        groupLayout2.addWidget(self.__destSelector)

        layout.addWidget(self.__errText)
        layout.addWidget(group1)
        layout.addWidget(group2)

        self.registerField("sourceSelection*", self, "sourceSelection")
        self.registerField("destSelection*", self, "destSelection")

    def initializePage(self):
        self.__errText.clear()

        src = self.__sourceSelector
        dst = self.__destSelector
        self.__initSelector(src)
        self.__initSelector(dst)

        depType = self.field("dependType")
        if depType == DependType.JOB_ON_JOB:
            src.setLayersEnabled(False)
            dst.setLayersEnabled(False)


    def __initSelector(self, selector):
        src = self.sourceObject()
        name = src.name
        
        isDest = selector is self.__destSelector 

        if isinstance(src, client.Job):
            if isDest:
                name = src.username
            selector.setJobFilter(name)
            return

        try:
            # FIXME: issue #66
            tasks = client.get_tasks(layers=[src], limit=1)
            job = tasks[0].get_job()
        except:
            job = src.get_job()

        selector.setJobFilter(job.name)

        if isinstance(src, client.Layer):
            if not isDest:
                selector.setLayerFilter(name)
            return

        if isinstance(src, client.Task):
            layer = client.get_layer_by_id(src.layerId)
            selector.setLayerFilter(layer.name)
            if not isDest:
                selector.setTaskFilter(name)

    def validatePage(self):
        src = self.sourceSelection 
        dst = self.destSelection
        self.__errText.clear()

        if not src or not dst:
            self.__errText.setText("<font color=red>Both dependant and "\
                                   "target selections are required</font>")
            return False

        if src[0] in dst:
            self.__errText.setText("<font color=red>Dependant item cannot "\
                                   "be set to depend on itself</font>")
            return False

        return super(ChooseTargetsPage, self).validatePage()

    def getSourceSelection(self):
        return self.__sourceSelector.getSelection()

    sourceSelection = QtCore.Property(list, fget=getSourceSelection)

    def getDestSelection(self):
        return self.__destSelector.getSelection()

    destSelection = QtCore.Property(list, fget=getDestSelection)


class ConfirmApplyPage(BaseDepPage):

    def __init__(self, *args, **kwargs):
        super(ConfirmApplyPage, self).__init__(*args, **kwargs)    

        self.setTitle("Confirming dependency")

        self.__text = text = QtGui.QPlainTextEdit(self)
        text.setWordWrapMode(QtGui.QTextOption.NoWrap)

        layout = QtGui.QVBoxLayout(self)
        layout.addWidget(self.__text)

if __name__ == "__main__":
    from plow.gui.util import loadTheme 

    app = QtGui.QApplication([])
    loadTheme()

    w = DependencyWizard()

    proj = client.get_project_by_code("weta")
    folders = proj.get_job_board()
    for folder in folders:     
        if folder.jobs:
            job = folder.jobs[-1]
            w.setSourceObject(job)
            # layer = job.get_layers()[0]
            # w.setSourceObject(layer)
            # task = layer.get_tasks()[0]
            # w.setSourceObject(task)
            break

    w.show()
    app.exec_()