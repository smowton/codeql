#!/usr/bin/python

# Based on https://github.com/kgonia/onlinezip/blob/master/onlinezip/OnlineZip.py, adapted for offline usage based on a suffix of the zip file we've already fetched.

import io
import struct
import sys
from copy import copy
from urllib.request import Request
from zipfile import ZipFile, ZipExtFile, sizeFileHeader, BadZipFile, _FH_SIGNATURE, structFileHeader, \
    stringFileHeader, _FH_FILENAME_LENGTH, _FH_EXTRA_FIELD_LENGTH, _FH_GENERAL_PURPOSE_FLAG_BITS, ZipInfo, \
    sizeEndCentDir, structEndArchive, _ECD_OFFSET, _ECD_SIZE, structEndArchive64, _CD64_DIRECTORY_SIZE, \
    _CD64_OFFSET_START_CENTDIR
import urllib.request

EOCD_RECORD_SIZE = sizeEndCentDir
ZIP64_EOCD_RECORD_SIZE = 56
ZIP64_EOCD_LOCATOR_SIZE = 20

MAX_STANDARD_ZIP_SIZE = 4_294_967_295

class PartialZip(ZipFile):
    def __init__(self, central_directory_bytes):
        super().__init__(self._get_central_directory(central_directory_bytes))

    def _get_central_directory(self, central_directory_bytes):
        eocd_record = central_directory_bytes[-EOCD_RECORD_SIZE:]
        endrec = struct.unpack(structEndArchive, eocd_record)
        endrec = list(endrec)
        if endrec[_ECD_SIZE] < MAX_STANDARD_ZIP_SIZE:
            self.cd_start = endrec[_ECD_OFFSET]
            self.cd_size = endrec[_ECD_SIZE]

            eocd_plus_cd_size = EOCD_RECORD_SIZE + self.cd_size
            if eocd_plus_cd_size > len(central_directory_bytes):
                raise Exception("Suffix not long enough (needed %d bytes)" % eocd_plus_cd_size)
            return io.BytesIO(central_directory_bytes[-eocd_plus_cd_size:])
        else:
            raise Exception("This is a zip64 archive")

def listzip(central_directory_bytes):
    return PartialZip(central_directory_bytes).namelist()
