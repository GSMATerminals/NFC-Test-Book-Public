/*
 *	Copyright 2014 Telecom Italia S.p.A.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *  http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.gsma.test.nfc;

import javacard.framework.ISOException;
import javacard.framework.Util;
import uicc.access.FileView;
import uicc.access.UICCException;

public class FakeFileView implements FileView {

	private static final short FILETYPE_TRANSPARENT = 1;
	private short mSelFid;
	private byte[] mSelFile;
	private short mFileType;
	private short mRecSize;

	static final short FILE_SIZE = 128;
	final private byte[] file_1F00 = new byte[FILE_SIZE];
	final private byte[] file_1F01 = new byte[FILE_SIZE];
	static final short FID_MASTER_FILE = 0x5F00;
	static final short FID_FILE_1F00 = (short) 0x1F00;
	static final short FID_FILE_1F01 = (short) 0x1F01;

	static final short SW_BUG = (short) 0x98FF;

	private void unsupported() {
		ISOException.throwIt(SW_BUG);
	}

	public void activateFile() throws UICCException {
		unsupported();
	}

	public void deactivateFile() throws UICCException {
		unsupported();
	}

	public short increase(byte[] arg0, short arg1, short arg2, byte[] arg3,
			short arg4) throws NullPointerException,
			ArrayIndexOutOfBoundsException, UICCException {
		unsupported();
		return 0;
	}

	public short searchRecord(byte arg0, short arg1, short arg2, byte[] arg3,
			short arg4, short arg5, short[] arg6, short arg7, short arg8)
			throws NullPointerException, ArrayIndexOutOfBoundsException,
			UICCException {
		unsupported();
		return 0;
	}

	public void select(byte arg0) throws UICCException {
		unsupported();
	}

	public short select(short arg0, byte[] arg1, short arg2, short arg3)
			throws NullPointerException, ArrayIndexOutOfBoundsException,
			UICCException {
		unsupported();
		return 0;
	}

	public short status(byte[] arg0, short arg1, short arg2)
			throws NullPointerException, ArrayIndexOutOfBoundsException,
			UICCException {
		unsupported();
		return 0;
	}

	public short readRecord(short recNumber, byte mode, short recOffset,
			byte[] resp, short respOffset, short respLength)
			throws NullPointerException, ArrayIndexOutOfBoundsException,
			UICCException {
		unsupported();
		return 0;
	}

	public void updateRecord(short recNumber, byte mode, short recOffset,
			byte[] data, short dataOffset, short dataLength)
			throws NullPointerException, ArrayIndexOutOfBoundsException,
			UICCException {
		unsupported();
	}

	// ##############################################################################
	// S U P P O R T E D C O M M A N D S
	// ##############################################################################

	private byte[] getFileArray(short fid) {
		switch (fid) {
		case FID_FILE_1F00:
			return file_1F00;
		case FID_FILE_1F01:
			return file_1F01;
		default:
			return null;
		}
	}

	private short getFileType(short fid) {
		switch (fid) {
		case FID_FILE_1F00:
		case FID_FILE_1F01:
			return FILETYPE_TRANSPARENT;
		default:
			unsupported();
			return -1;
		}
	}

	public void select(short fid) throws UICCException {
		if (fid == FID_MASTER_FILE) {
			mSelFid = fid;
		} else {
			switch (mSelFid) {
			case FID_MASTER_FILE:
			case FID_FILE_1F00:
			case FID_FILE_1F01:
				if (fid == FID_FILE_1F00 || fid == FID_FILE_1F01) {
					mSelFid = fid;
				} else {
					UICCException.throwIt(UICCException.FILE_NOT_FOUND);
				}
				break;
			default:
				UICCException.throwIt(UICCException.FILE_NOT_FOUND);
			}
		}
		mSelFile = getFileArray(mSelFid);
		if (mSelFile != null) {
			mFileType = getFileType(mSelFid);
			mRecSize = (short) (mFileType >>> 8);
			mRecSize = (mRecSize == 0) ? 256 : mRecSize;
			mFileType &= 0xFF;
		}
	}

	public short readBinary(short fileOffset, byte[] resp, short respOffset,
			short respLength) throws NullPointerException,
			ArrayIndexOutOfBoundsException, UICCException {
		checkEFSelected();
		checkCommandCompatible(FILETYPE_TRANSPARENT);

		if (fileOffset < 0 || fileOffset + respLength > mSelFile.length) {
			UICCException.throwIt(UICCException.OUT_OF_FILE_BOUNDARIES);
		}
		Util.arrayCopy(mSelFile, fileOffset, resp, respOffset, respLength);
		return (short) (respOffset + respLength);
	}

	public void updateBinary(short fileOffset, byte[] data, short dataOffset,
			short dataLength) throws NullPointerException,
			ArrayIndexOutOfBoundsException, UICCException {

		checkEFSelected();
		checkCommandCompatible(FILETYPE_TRANSPARENT);

		if (fileOffset + dataLength > mSelFile.length) {
			UICCException.throwIt(UICCException.OUT_OF_FILE_BOUNDARIES);
		}
		Util.arrayCopy(data, dataOffset, mSelFile, fileOffset, dataLength);

	}

	private void checkEFSelected() {
		if (mSelFile == null) {
			UICCException.throwIt(UICCException.NO_EF_SELECTED);
		}
	}

	private void checkCommandCompatible(short type) {
		if (mFileType != type) {
			UICCException.throwIt(UICCException.COMMAND_INCOMPATIBLE);
		}
	}

	public void reset() {
		mSelFid = 0;
		mSelFile = null;
	}

}
