package actors.reporter

import com.paritytrading.parity.util.Instruments
import actors.reporter.MarketReporter.TradeEvent
import com.paritytrading.parity.util.Instrument

class MailFormat(instruments: Instruments) {

  def format(event: TradeEvent): String = {
    val instrument: Instrument = instruments.get(event.instrument);

    val sb = new StringBuilder
    sb ++= "<p>Dear Ovonpar User,</p>"
    sb ++= "<p>Your Ovonpar Stock Ticker order has been matched with another order. Below we are sending details of the match:</p>"
    sb ++= "<table><tr><td>Timestamp</td><td>Instrument</td><td>Quantity</td><td>Price PLN</td><td>Buyer</td><td>Seller</td></tr><tr>"
    sb ++= String.format("<td>%12s</td><td>%-8s</td>", event.timestamp, event.instrument)
    sb ++= "<td>"
    sb ++= instrument.getSizeFormat().format(event.quantity / instrument.getSizeFactor())
    sb ++= "</td>"
    sb ++= "<td>"
    sb ++= instrument.getPriceFormat().format(event.price / instrument.getPriceFactor())
    sb ++= "</td>"
    sb ++= "<td>%-20s</td><td>%-20s</td>"
    sb ++= "</tr></table>"
    sb ++= "<p>Sincerely,</p><p>The Ovonpar Stock Ticker Team</p>"
    sb.toString

  }

}