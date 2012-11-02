"""
System profile plugin for Mac OSX.  Should work on Tiger or higher.
"""
import os
import ctypes
import logging

from ctypes.util import find_library

from .posix import SystemProfiler as PosixSystemProfiler

logger = logging.getLogger(__name__)

__all__ = ["SystemProfiler"]

memstat = ctypes.cdll.LoadLibrary(
    os.path.join(os.path.dirname(__file__), "libmemstat.dylib"))

class sysinfo_t(ctypes.Structure):
    _fields_ = [
        ("totalRamMb", ctypes.c_uint64),
        ("freeRamMb", ctypes.c_uint64),
        ("totalSwapMb", ctypes.c_uint64),
        ("freeSwapMb", ctypes.c_uint64),
        ("physicalCpus", ctypes.c_uint32),
        ("logicalCpus", ctypes.c_uint32),
        ("bootTime", ctypes.c_uint64),
        ("cpuModel", ctypes.c_char_p)]

class SystemProfiler(PosixSystemProfiler):

    def __repr__(self):
        return "<%s: OSX>" % self.__class__.__name__

    def _update(self):
        f = sysinfo_t()
        memstat.foo(ctypes.byref(f))
        self.data.update(dict([(field[0], 
            getattr(f, field[0])) for field in sysinfo_t._fields_]))





