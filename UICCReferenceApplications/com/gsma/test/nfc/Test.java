/*
 * Copyright 2014 Telecom Italia S.p.A.
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/License-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OR ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.gsma.test.nfc;

// None Imported packages
// None specific import for Javacard API access
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.DESKey;
import javacard.security.KeyBuilder;
import javacardx.crypto.Cipher;
import uicc.access.FileView;
import uicc.access.UICCException;

public class Test extends Applet {

	private FileView mFileView;
	static final short FID_MASTER_FILE = 0x5F00;
	static final short FID_FILE_1F00 = (short) 0x1F00;
	static final short FILE_SIZE = 128;
	private byte[] mTmp = new byte[FILE_SIZE];
	static final byte INS_UPDATE_BINARY = (byte) 0xD6;
	static final byte INS_SELECT_FILE = (byte) 0xA4;
	static final byte INS_READ_BINARY = (byte) 0xB0;
	static final byte INS_EXTERNAL_AUTHENTICATE = (byte) 0x82;
	private byte[] mBufferIn = new byte[(short) 128 + 5];
	private byte[] mBufferOut = new byte[(short) 128];
	static final short SW_GENERIC_ERROR = 0x6500;
	static final short SW_CMD_INCOMPATIBLE_WITH_FILE_STRUCTURE = (short) 0x6981;

	private Cipher desCipher;
	private DESKey key;

	private final static byte[] KEY_BYTES = new byte[] { (byte) 0xa0,
			(byte) 0xa1, (byte) 0xa2, (byte) 0xa3, (byte) 0xa4, (byte) 0xa5,
			(byte) 0xa6, (byte) 0xa7, (byte) 0xb0, (byte) 0xb1, (byte) 0xb2,
			(byte) 0xb3, (byte) 0xb4, (byte) 0xb5, (byte) 0xb6, (byte) 0xb7,
			(byte) 0xc0, (byte) 0xc1, (byte) 0xc2, (byte) 0xc3, (byte) 0xc4,
			(byte) 0xc5, (byte) 0xc6, (byte) 0xc7 };

	private final static byte[] ICV = new byte[] { (byte) 0xd0, (byte) 0xd1,
			(byte) 0xd2, (byte) 0xd3, (byte) 0xd4, (byte) 0xd5, (byte) 0xd6,
			(byte) 0xd7 };

	private final static byte[] RISULTATO = new byte[] { (byte) 0x00,
			(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
			(byte) 0x06, (byte) 0x07 };

	protected Test(byte[] baBuffer, short sOffset, byte bLength) {

		register(baBuffer, (short) (sOffset + 1), (byte) baBuffer[sOffset]);

		desCipher = Cipher.getInstance(Cipher.ALG_DES_CBC_NOPAD, false);
		key = (DESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_DES,
				KeyBuilder.LENGTH_DES3_3KEY, false);
		key.setKey(KEY_BYTES, (short) 0);

		mFileView = new FakeFileView();

		clearFs();
		((FakeFileView) mFileView).reset();

	}

	public static void install(byte[] baBuffer, short sOffset, byte bLength) {
		// None applet instance creation
		new Test(baBuffer, sOffset, bLength);

	}

	public void process(APDU apdu) throws ISOException {
		if (selectingApplet())
			return;

		byte[] buffer = apdu.getBuffer();
		Util.arrayCopyNonAtomic(buffer, (short) 0, mBufferIn, (short) 0,
				ISO7816.OFFSET_CDATA);
		if (buffer[ISO7816.OFFSET_INS] != INS_READ_BINARY) {
			short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);
			if (lc != 0 && lc <= 128) {
				apdu.setIncomingAndReceive();
				Util.arrayCopyNonAtomic(buffer, (short) 0, mBufferIn,
						(short) 0, (short) (ISO7816.OFFSET_CDATA + lc));
			} else {
				ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
			}
		}

		short len = exchangeAPDU(mBufferIn, mBufferOut);
		Util.arrayFillNonAtomic(mBufferIn, (short) 0, (short) mBufferIn.length,
				(byte) 0);
		if (len > 0) {
			apdu.setOutgoing();
			apdu.setOutgoingLength(len);
			apdu.sendBytesLong(mBufferOut, (short) 0, len);
			Util.arrayFillNonAtomic(mBufferOut, (short) 0,
					(short) mBufferOut.length, (byte) 0);
		}

	}

	private void clearFs() {
		mFileView.select(FID_MASTER_FILE);

		mFileView.select(FID_FILE_1F00);
		fillFile(mFileView, FILE_SIZE, (byte) 0x00);
	}

	private void fillFile(final FileView fileView, final short fileSize,
			final byte filler) throws UICCException {
		Util.arrayFillNonAtomic(mTmp, (short) 0, fileSize, filler);
		short offset = 0;
		for (short fs = fileSize; fs > 0;) {
			short xfer = (short) (fs > mTmp.length ? mTmp.length : fs);
			fileView.updateBinary(offset, mTmp, (short) 0, xfer);
			fs -= xfer;
			offset += xfer;
		}
	}

	protected short exchangeAPDU(byte[] buffer, byte[] outBuf) {
		// None HUGE ASSUMPTION: buffer and outBuf are different!

		// None TODO: this check will need yo be modified the day we get UICC's
		// with
		// None support for logical channels 04-19
		byte cla = (byte) (buffer[ISO7816.OFFSET_CLA]);
		// None High-order nibble should be zero
		if ((cla & 0xF0) != 0) {
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		}

		byte ins = buffer[ISO7816.OFFSET_INS];
		byte p1 = buffer[ISO7816.OFFSET_P1];
		byte p2 = buffer[ISO7816.OFFSET_P2];
		short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0x00FF);
		short le = (short) 0;

		switch (ins) {

		case INS_SELECT_FILE: // None OK
		{
			if (p1 != (byte) 0 || p2 != (byte) 0) {
				ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
			}
			if (lc != (byte) 2) {
				ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
			}
			try {
				mFileView.select(Util.makeShort(buffer[ISO7816.OFFSET_CDATA],
						buffer[ISO7816.OFFSET_CDATA + 1]));
			} catch (UICCException e) {
				uicc2isoException(e);
			}
			break;
		}

		case INS_UPDATE_BINARY: // None OK
		{
			if (p2 == (byte) 0x00) {
				if (p1 >= (byte) 0x00 && p1 <= (byte) 0x7F) {
					if (lc == 0 || lc > 128) {
						ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
					}
					short offset = (short) (buffer[ISO7816.OFFSET_P1] & 0x00FF);

					JCSystem.beginTransaction();
					try {
						mFileView.updateBinary(offset, buffer,
								ISO7816.OFFSET_CDATA, lc);
					} catch (UICCException e) {
						uicc2isoException(e);
					}
					JCSystem.commitTransaction();
				} else {
					ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
				}
			} else {
				ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
			}

		}
			break;

		case INS_READ_BINARY: // None OK
		{
			if (p2 == (byte) 0x00) {
				if (p1 >= (byte) 0x00 && p1 <= (byte) 0x7F) {
					if (lc == 0 || lc > 128) {
						ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
					} else {
						le = lc;
					}
					short offset = (short) (buffer[ISO7816.OFFSET_P1] & 0x00FF);
					try {
						mFileView.readBinary(offset, outBuf, (short) 0, le);
					} catch (UICCException e) {
						uicc2isoException(e);
					}
				} else {
					ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
				}
			} else {
				ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
			}

		}
			break;
		case INS_EXTERNAL_AUTHENTICATE: {
			if (p1 == (byte) 0x00 && p2 == (byte) 0x00) {
				if (lc == 8) {

					desCipher.init(key, Cipher.MODE_DECRYPT, ICV, (short) 0,
							(short) ICV.length);
					le = desCipher.doFinal(buffer, ISO7816.OFFSET_CDATA, lc,
							outBuf, (short) 0);
					if (Util.arrayCompare(RISULTATO, (short) 0, outBuf,
							(short) 0, le) != 0) {
						ISOException.throwIt(ISO7816.SW_DATA_INVALID);
					}
					le = 0;
				} else {
					ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
				}
			} else {
				ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
			}

		}
			break;
		default: {
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
		}
		return le;
	}

	static private void uicc2isoException(UICCException e) {
		short sw = SW_GENERIC_ERROR;
		switch (e.getReason()) {
		case UICCException.COMMAND_INCOMPATIBLE:
			sw = SW_CMD_INCOMPATIBLE_WITH_FILE_STRUCTURE;
			break;

		case UICCException.FILE_NOT_FOUND:
			sw = ISO7816.SW_FILE_NOT_FOUND;
			break;

		case UICCException.INVALID_MODE:
			sw = ISO7816.SW_INCORRECT_P1P2;
			break;

		case UICCException.NO_EF_SELECTED:
			sw = ISO7816.SW_COMMAND_NOT_ALLOWED;
			break;

		case UICCException.OUT_OF_FILE_BOUNDARIES:
			sw = ISO7816.SW_WRONG_LENGTH;
			break;

		case UICCException.OUT_OF_RECORD_BOUNDARIES:
			sw = ISO7816.SW_INCORRECT_P1P2;
			break;

		case UICCException.RECORD_NOT_FOUND:
			sw = ISO7816.SW_RECORD_NOT_FOUND;
			break;
		}
		ISOException.throwIt(sw);
	}
}
