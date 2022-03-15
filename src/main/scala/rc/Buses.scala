package constellation.rc

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import constellation.{NoCConfig}

case class ConstellationSystemBusParams(
  params: SystemBusParams,
  inNodeMapping: Seq[Int],
  outNodeMapping: Seq[Int],
  privateNoC: Option[NoCConfig]
) extends TLBusWrapperInstantiationLike {
  def instantiate(context: HasTileLinkLocations, loc: Location[TLBusWrapper])(implicit p: Parameters): SystemBus = {

    val base_noc_params = TLNoCParams("sbus", inNodeMapping, outNodeMapping, privateNoC)
    val noc_params = context match {
      case c: CanHaveGlobalTLInterconnect =>
        base_noc_params.copy(globalTerminalChannels = Some(() => c.getSubnetTerminalChannels(SBUS)))
      case _ => base_noc_params
    }
    val constellation = LazyModule(new ConstellationSystemBus(params, noc_params))

    constellation.suggestName(loc.name)
    context.tlBusWrapperLocationMap += (loc -> constellation)
    constellation
  }
}


class ConstellationSystemBus(sbus_params: SystemBusParams, noc_params: TLNoCParams)
  (implicit p: Parameters) extends SystemBus(sbus_params) {
  val system_bus_noc = LazyModule(new TLNoC(noc_params))

  override val inwardNode: TLInwardNode = system_bus_noc.node
  override val outwardNode: TLOutwardNode = system_bus_noc.node
  override def busView: TLEdge = system_bus_noc.node.edges.in.head

}