package constellation.rc

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import constellation.{NoCKey}
import constellation.channel.{TerminalChannel}

// Use a noc based system bus. By default instantiates a private noc within the bus
class WithConstellationNoCSystemBus(inNodeMapping: Seq[Int], outNodeMapping: Seq[Int]) extends Config((site, here, up) => {
  case TLNetworkTopologyLocated(InSubsystem) => {
    up(TLNetworkTopologyLocated(InSubsystem), site).map(topo => {
      topo match {
        case j: TLBusWrapperTopology => {
          new TLBusWrapperTopology(j.instantiations.map(inst => inst match {
            case (SBUS, sbus_params: SystemBusParams) =>
              (SBUS, ConstellationSystemBusParams(
                sbus_params, inNodeMapping, outNodeMapping, Some(up(NoCKey))))
            case (SBUS, sbus_params: ConstellationSystemBusParams) =>
              (SBUS, sbus_params.copy(inNodeMapping=inNodeMapping, outNodeMapping=outNodeMapping))
            case a => a
          }), j.connections)
        }
        case x => x
      }
    })
  }
})


// connect them to the global noc instead
class WithSbusGlobalNoC extends Config((site, here, up) => {
  case InstantiateGlobalTLInterconnect => {
    val instantiation = up(TLNetworkTopologyLocated(InSubsystem)).map(topo => topo match {
      case j: TLBusWrapperTopology => j.instantiations
      case _ => Nil
    }).flatten.filter(_._1 == SBUS)(0)
    val (inNodeMapping, outNodeMapping) = instantiation._2 match {
      case s: ConstellationSystemBusParams => (s.inNodeMapping, s.outNodeMapping)
      case _ => require(false); (Nil, Nil)
    }
    up(InstantiateGlobalTLInterconnect).copy(busMap =
      up(InstantiateGlobalTLInterconnect).busMap + (SBUS -> (inNodeMapping, outNodeMapping))
    )
  }
  case TLNetworkTopologyLocated(InSubsystem) => {
    up(TLNetworkTopologyLocated(InSubsystem), site).map(topo => {
      topo match {
        case j: TLBusWrapperTopology => {
          new TLBusWrapperTopology(j.instantiations.map(inst => inst match {
            case (SBUS, sbus_params: ConstellationSystemBusParams) => {
              (SBUS, sbus_params.copy(privateNoC=None))
            }
            case a => a
          }), j.connections)
        }
        case x => x
      }
    })
  }
})
