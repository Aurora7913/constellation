package constellation

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.{Field, Parameters}

case class VirtualChannelParams(
  bufferSize: Int = 1,
  traversable: Boolean = false
)

case class ChannelParams(
  srcId: Int,
  destId: Int,
  virtualChannelParams: Seq[VirtualChannelParams] = Seq(VirtualChannelParams()),
  terminalInputId: Int = -1,
  terminalOutputId: Int = -1,
  depth: Int = 0
) {
  val nVirtualChannels = virtualChannelParams.size
  val isTerminalInput = terminalInputId >= 0
  val isTerminalOutput = terminalOutputId >= 0
  def traversable = virtualChannelParams.map(_.traversable).reduce(_||_)
  require(!(srcId == -1 ^ isTerminalInput))
  require(!(destId == -1 ^ isTerminalOutput))
  require(!(isTerminalInput && isTerminalOutput))
}

trait HasChannelParams extends HasNoCParams {
  val cParam: ChannelParams

  val virtualChannelParams = cParam.virtualChannelParams
  val nVirtualChannels = cParam.nVirtualChannels
  val virtualChannelBits = log2Up(nVirtualChannels)

  val maxBufferSize = virtualChannelParams.map(_.bufferSize).max

  val isTerminalInputChannel = cParam.isTerminalInput
  val isTerminalOutputChannel = cParam.isTerminalOutput
  val isTerminalChannel = isTerminalInputChannel || isTerminalOutputChannel
}

class Channel(val cParam: ChannelParams)(implicit val p: Parameters) extends Bundle with HasChannelParams {
  require(!isTerminalChannel)

  val flit = Valid(new Flit(cParam))
  val credit_return = Input(Valid(UInt(virtualChannelBits.W)))
  val vc_free = Input(Valid(UInt(virtualChannelBits.W)))
}

class IOChannel(val cParam: ChannelParams)(implicit val p: Parameters) extends Bundle with HasChannelParams {
  require(isTerminalChannel)

  val flit = Decoupled(new Flit(cParam))
}

object ChannelBuffer {
  def apply(in: Channel, cParam: ChannelParams)(implicit p: Parameters): Channel = {
    val buffer = Module(new ChannelBuffer(cParam))
    buffer.io.in <> in
    buffer.io.out
  }
}

class ChannelBuffer(val cParam: ChannelParams)(implicit val p: Parameters) extends Module with HasChannelParams {
  val io = IO(new Bundle {
    val in = Flipped(new Channel(cParam))
    val out = new Channel(cParam)
  })
  if (cParam.traversable) {
    io.out.flit := Pipe(io.in.flit, cParam.depth)
    io.in.credit_return := Pipe(io.out.credit_return, cParam.depth)
    io.in.vc_free := Pipe(io.out.vc_free, cParam.depth)
  } else {
    io.out.flit.valid := false.B
    io.out.flit.bits := DontCare
    io.in.credit_return.valid := false.B
    io.in.credit_return.bits := DontCare
    io.in.vc_free.valid := false.B
    io.in.vc_free.bits := DontCare
  }

}
