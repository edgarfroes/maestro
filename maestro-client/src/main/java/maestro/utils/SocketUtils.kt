/*
 *
 *  Copyright (c) 2022 mobile.dev inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package maestro.utils

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

object SocketUtils {

    /**
     * Checks whether the port can be connected to.
     */
    fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { true }
        } catch (ignored: IOException) {
            false
        }
    }

    fun localIp(): String {
        return NetworkInterface.getNetworkInterfaces()
            .toList()
            .firstNotNullOfOrNull { networkInterface ->
                networkInterface.inetAddresses
                    .toList()
                    .find { inetAddress ->
                        !inetAddress.isLoopbackAddress
                            && inetAddress is Inet4Address
                            && inetAddress.hostAddress.startsWith("192")
                    }
                    ?.hostAddress
            }
            ?: InetAddress.getLocalHost().hostAddress
    }

}