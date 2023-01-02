import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import kotlin.math.*
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException


/**
 * 사기, 팔기 통신 : localhost:8888
 * 이 통신을 VirtualStockFinal과 할 예정
 */


fun sleep(time: Int) {
    val nextTime = LocalDateTime.now().plusSeconds(time.toLong())
    while (nextTime >= LocalDateTime.now()) {
    }
}


object Parameters {
    var buyDelay: Int = 10                     // 사는 것이 순간 구매량에 반영되는 지연시간
    var sellDelay: Int = 3                     // 파는 것이 순간 구매량에 반영되는 지연시간
    var buyLife: Int = 70                      // 이 시간을 지난 구매량은 반영되지 않음
    var sellLife: Int = 60                     // 이 시간을 지난 판매량은 반영되지 않음
    var initialPrice: Int = 1000               // 초기 가격
    var packetSize: Int = 100                  // 패킷 사이즈    패킷 : "mode,amount,ip_addr"
    var packetAmount: Int = 3                  // 패킷 안에 포함된 데이터 양
    var updateFast: Int = 1                    // 업데이트 주기, 보여주는 주기
    var limitSijang: Int = 200                 // 한계 코인량
    var buySellRate: Double = 2.0              // buy, sell의 비율 (2.0일 때는 buy가 2배로 가격에 반영됨)
    var updateRate: Double = 3.0               // 업데이트하는 것이 이만큼 나누어져서 들어감
    var updateBias: Int = -3                   // 업데이트 시 변화량의 편향
    val priceSendFast:Int=1                    // 1초 간격으로 localhost:5000에 보냄


    fun toStringArray(): Array<String> {
        return arrayOf(
            "buyDelay : ${buyDelay}",
            "sellDelay : ${sellDelay}",
            "buyLife : ${buyLife}",
            "sellLife : ${sellLife}",
            "updateFast : ${updateFast}",
            "limitSijang : ${limitSijang}",
            "buySellRate : ${buySellRate}",
            "updateRate : ${updateRate}",
            "updateBias : ${updateBias}"
        )
    }
}


fun main() {
    var buyHistory: MutableList<History> = mutableListOf<History>()
    var sellHistory: MutableList<History> = mutableListOf<History>()
    var oldHistoryBuy: MutableList<History> = mutableListOf<History>()
    var oldHistorySell: MutableList<History> = mutableListOf<History>()
    var money: Int = 0
    var inSijang: Int = 0
    var buySpeed: Int = 0
    var sellSpeed: Int = 0
    var control: Boolean = false
    val datagramSocket: DatagramSocket = DatagramSocket()


    val port = 8888
    val socket: DatagramSocket = DatagramSocket(port)

    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            while (control) continue
            val packet: DatagramPacket = DatagramPacket(ByteArray(Parameters.packetSize), Parameters.packetSize)
            withContext(Dispatchers.IO) {
                socket.receive(packet)
            }
            var _data: List<Byte> = packet.data.toList()
            try {
                var i = 0
                while (_data[i].toInt() != 0) i += 1
                _data = _data.slice(IntRange(0, i - 1))
            } catch (e: Exception) {
                println("wrong packet")
                continue
            }
            val data: ByteArray = _data.toByteArray()
            val message: List<String> = String(data).split(",")
            try {
                val x = message[1].toInt()
                if (message.size != Parameters.packetAmount) {
                    println("wrong packet")
                    continue
                }
                if (message[0] == "buy") {
                    val t = mutableListOf<History>(
                        History(LocalDateTime.now().plusSeconds(Parameters.buyDelay.toLong()), x, message[2])
                    )
                    t.addAll(oldHistoryBuy)
                    oldHistoryBuy = t
                    money += Stock.price.toInt() * x
                    inSijang += x
                    //buySpeed += x
                    if (history.containsKey(message[2])) {
                        history[message[2]]!!.addAll(mutableListOf<History>(History(LocalDateTime.now(), x, "buy")))
                    } else {
                        history.put(message[2], mutableListOf(History(LocalDateTime.now(), x, "buy")))
                    }
                } else if (message[0] == "sell") {
                    val t = mutableListOf<History>(
                        History(LocalDateTime.now().plusSeconds(Parameters.sellDelay.toLong()), x, message[2])
                    )
                    t.addAll(oldHistorySell)
                    oldHistorySell = t
                    money -= Stock.price.toInt() * x
                    inSijang -= x
                    //sellSpeed += x
                    if (history.containsKey(message[2])) {
                        history[message[2]]!!.addAll(mutableListOf<History>(History(LocalDateTime.now(), x, "sell")))
                    } else {
                        history.put(message[2], mutableListOf(History(LocalDateTime.now(), x, "sell")))
                    }
                } else {
                    println("wrong packet")
                    continue
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("wrong packet")
                continue
            }
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            while (control) continue
            if (oldHistoryBuy.size != 0) if (oldHistoryBuy.last().time < LocalDateTime.now()) {
                val old = oldHistoryBuy[oldHistoryBuy.size - 1]
                oldHistoryBuy.removeAt(oldHistoryBuy.size - 1)
                buySpeed += old.amount
                val t = mutableListOf<History>(
                    History(old.time.plusSeconds(Parameters.buyLife.toLong()), old.amount, old.ipAddr)
                )
                t.addAll(buyHistory)
                buyHistory = t
            }
            if (oldHistorySell.size != 0) if (oldHistorySell.last().time < LocalDateTime.now()) {
                val old = oldHistorySell[oldHistorySell.size - 1]
                oldHistorySell.removeAt(oldHistorySell.size - 1)
                sellSpeed += old.amount
                val t = mutableListOf<History>(
                    History(old.time.plusSeconds(Parameters.sellLife.toLong()), old.amount, old.ipAddr)
                )
                t.addAll(sellHistory)
                sellHistory = t
            }
            print("")
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            while (control) continue
            if (buyHistory.size != 0) if (buyHistory.last().time < LocalDateTime.now()) {
                buySpeed -= buyHistory[buyHistory.size - 1].amount
                buyHistory.removeAt(buyHistory.size - 1)
            }
            if (sellHistory.size != 0) if (sellHistory.last().time < LocalDateTime.now()) {
                sellSpeed -= sellHistory[sellHistory.size - 1].amount
                sellHistory.removeAt(sellHistory.size - 1)
            }
            print("")
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            sleep(Parameters.updateFast)
            while (control) continue
            Stock.update(buySpeed, sellSpeed, inSijang)
            val connection = DriverManager.getConnection(
                "jdbc:mysql://localhost/pdb",
                "asdf", "asdf"
            )
            val sqlQ:String="UPDATE nowprice SET price = ${Stock.price} WHERE 1"
            val pstmt=connection.prepareStatement(sqlQ)
            pstmt.executeUpdate()
            pstmt.close()
            connection.close()
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val message = Stock.price.toString().toByteArray()
            val datagram=DatagramPacket(
                message, message.size,
                InetSocketAddress("localhost", 5000)
            )
            withContext(Dispatchers.IO) {
                datagramSocket.send(datagram)
            }
            sleep(Parameters.priceSendFast)
        }
    }
    // 명령어 컨트롤
    /*
     * pause
     *    - 일시정지
     *    - 큰 일 없으면 굳이 쓰지
     * print history all
     *    - 전체 히스토리를 포함하는 파일을 출력합니다.
     * print history [ip_addr]
     *    - 특정 ip_addr을 가진 사람의 히스토리를 포함하는 파일을 출력합니다.
     * print allCoins
     *    - 시장에 얼마나 많은 코인이 있는지 출력합니다.
     * print price
     *    - 현재 가격을 출력합니다.
     * print buySpeed
     *    - 1분 사이의 총 사람들의 코인 구매량을 출력합니다.
     * print sellSpeed
     *    - 1분 사이의 총 사람들의 코인 판매량을 출력합니다.
     * print revenue
     *    - 총 수익을 출력합니다.
     * print parameters
     *    - 파라미터를 출력합니다. 자세한 것은 소스코드 파라미터 설명 부분 주석 참고
     * set ~ [val]
     */
    println("start")
    while (true) {
        try {
            val exe = readLine()
            if (exe == null || exe == "") continue
            else if (exe == "pause") {
                control = !control
            }
            val execute = exe.split(' ')
            when (execute[0]) {
                "help" -> {
                    println("pause")
                    println("   - 일시정지")
                    println("Gn add [num]")
                    println("   - 귀신의 집 수익을 추가합니다.")
                    println("Gn sub [num]")
                    println("   - 귀신의 집 수익을 뺍니다.")
                    println("print history all")
                    println("   - 전체 히스토리를 포함하는 파일을 출력합니다.")
                    println("print history [ip_addr]")
                    println("   - 특정 ip_addr을 가진 사람의 히스토리를 포함하는 파일을 출력합니다.")
                    println("print allCoins ")
                    println("   - 시장에 얼마나 많은 코인이 있는지 출력합니다.")
                    println("print price")
                    println("   - 현재 가격을 출력합니다.")
                    println("print buySpeed")
                    println("   - 1분 사이의 총 사람들의 코인 구매량을 출력합니다.")
                    println("print sellSpeed")
                    println("   - 1분 사이의 총 사람들의 코인 판매량을 출력합니다.")
                }

                "print" -> {
                    when (execute[1]) {
                        "history" -> {
                            val path = "historyView.txt"
                            try {
                                Files.write(Paths.get(path), "".toByteArray(), StandardOpenOption.CREATE)
                                Files.deleteIfExists(Paths.get(path))
                            } catch (e: IOException) {
                                println("error")
                                continue
                            }
                            if (execute[2] == "all") {
                                Files.write(Paths.get(path), run {
                                    var x = ""
                                    for (k in history.keys) {
                                        var buy = 0
                                        var sell = 0
                                        for (h in history[k]!!) {
                                            if (h.ipAddr == "buy") {
                                                buy += h.amount
                                            } else if (h.ipAddr == "sell") {
                                                sell += h.amount
                                            }
                                        }
                                        x += "$k : buy=$buy, sell=$sell\n"
                                    }
                                    x
                                }.toByteArray(), StandardOpenOption.CREATE)
                                println("파일 생성 완료")
                            } else if (history[execute[2]] == null) {
                                println("no person")
                            } else {
                                Files.write(Paths.get(path), run {
                                    var x = ""
                                    for (i in history[execute[2]]!!) {
                                        x += "${i.ipAddr} : ${i.amount} (${i.time})\n"
                                    }
                                    x
                                }.toByteArray(), StandardOpenOption.CREATE)
                                println("파일 생성 완료")
                            }
                        }

                        "allCoins" -> {
                            println(inSijang)
                        }

                        "price" -> {
                            println(Stock.price)
                        }

                        "buySpeed" -> {
                            println(buySpeed)
                        }

                        "sellSpeed" -> {
                            println(sellSpeed)
                        }

                        "revenue" -> {
                            println(money)
                        }

                        "parameters" -> {
                            for (param in Parameters.toStringArray()) {
                                println(param)
                            }
                        }

                        else -> {
                            println("뭘 출력하라고")
                        }
                    }
                }

                "set" -> {
                    when (execute[1]) {
                        "parameter" -> {
                            when (execute[2]) {
                                "buyDelay" -> {
                                    val old = Parameters.buyDelay
                                    Parameters.buyDelay = execute[3].toInt()
                                    println("Parameters.buyDelay : $old -> ${Parameters.buyDelay}")
                                }

                                "sellDelay" -> {
                                    val old = Parameters.sellDelay
                                    Parameters.sellDelay = execute[3].toInt()
                                    println("Parameters.sellDelay : $old -> ${Parameters.sellDelay}")
                                }

                                "buyLife" -> {
                                    val old = Parameters.buyLife
                                    Parameters.buyLife = execute[3].toInt()
                                    println("Parameters.buyLife : $old -> ${Parameters.buyLife}")
                                }

                                "sellLife" -> {
                                    val old = Parameters.sellLife
                                    Parameters.sellLife = execute[3].toInt()
                                    println("Parameters.sellLife : $old -> ${Parameters.sellLife}")
                                }

                                "limitSijang" -> {
                                    val old = Parameters.limitSijang
                                    Parameters.limitSijang = execute[3].toInt()
                                    println("Parameters.limitSijang : $old -> ${Parameters.limitSijang}")
                                }

                                "buySellRate" -> {
                                    val old = Parameters.buySellRate
                                    Parameters.buySellRate = execute[3].toDouble()
                                    println("Parameters.buySellRate : $old -> ${Parameters.buySellRate}")
                                }

                                "updateRate" -> {
                                    val old = Parameters.updateRate
                                    Parameters.updateRate = execute[3].toDouble()
                                    println("Parameters.updateRate : $old -> ${Parameters.updateRate}")
                                }

                                else -> {
                                    println("뭘 바꾸라고")
                                }
                            }
                        }

                        "price" -> {
                            val old = Stock.price
                            Stock.price = BigInteger(execute[2])
                            println("Stock.price : $old -> ${Stock.price}")
                        }

                        "buySpeed" -> {
                            val old = buySpeed
                            buySpeed = execute[3].toInt()
                            println("buySpeed : $old -> $buySpeed")
                        }

                        "sellSpeed" -> {
                            val old = sellSpeed
                            sellSpeed = execute[3].toInt()
                            println("sellSpeed : $old -> $sellSpeed")
                        }

                        else -> {
                            println("뭘 수정하라고")
                        }
                    }
                }

                else -> {
                    println("wrong command")
                }
            }
        } catch (e: Exception) {
            println("wrong command")
        }
    }
}

data class History(val time: LocalDateTime, val amount: Int, val ipAddr: String) {}


val history = HashMap<String, MutableList<History>>()
// History.ipaddr에 buy, sell 저장
// key에 ipaddr 저장


object Stock {
    var price: BigInteger = Parameters.initialPrice.toBigInteger()

    fun update(buy: Int, sell: Int, inSijang: Int) {
        val k1 = 1.0
        val k2 = 25.727
        val newPrice: BigInteger = exp(((buy.toDouble() / Parameters.buySellRate - sell.toDouble()) / 10 + k2) / (exp(k1 - inSijang / Parameters.limitSijang) + 1)).toInt().toBigInteger()
        price += (newPrice - price) / (Parameters.updateRate.toInt().toBigInteger()) - Parameters.updateBias.toBigInteger()
    }
}
