/*
 * Copyright 2013 Wolfgang Flohr-Hochbichler (developer@jshybugger.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jshybugger.server;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * The helper class Md5Checksum calculates the MD5 checksum for file resources.
 */
public class Md5Checksum {

	/**
	 * Calculates the MD5 checksum for an resource.
	 *
	 * @param fis the fis
	 * @return the md5 byte[] array
	 * @throws Exception the exception
	 */
	private static byte[] createChecksum(InputStream fis) throws Exception {

		byte[] buffer = new byte[1024];
		MessageDigest complete = MessageDigest.getInstance("MD5");
		int numRead;

		do {
			numRead = fis.read(buffer);
			if (numRead > 0) {
				complete.update(buffer, 0, numRead);
			}
		} while (numRead != -1);

		return complete.digest();
	}

	/**
	 * Gets the MD5 checksum for a resource.
	 *
	 * @param fis the resource stream
	 * @return the md5 checksum
	 * @throws Exception the exception
	 */
	public static String getMD5Checksum(InputStream fis) throws Exception {
		byte[] b = createChecksum(fis);
		String result = "";

		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}
}
