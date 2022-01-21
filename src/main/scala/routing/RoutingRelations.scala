package constellation.routing

import scala.math.pow

object RoutingRelations {

  // Utility functions
  val srcIsIngress = new RoutingRelation((nodeID, srcC, nxtC, pInfo) => srcC.src == -1)
  val nxtIsVC0     = new RoutingRelation((nodeID, srcC, nxtC, pInfo) => nxtC.vc == 0)
  val srcIsVC0     = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => srcC.vc == 0)
  val nxtVLTSrcV   = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => nxtC.vc < srcC.vc)
  val nxtVLESrcV   = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => nxtC.vc <= srcC.vc)

  def escapeChannels(escapeRouter: RoutingRelation, normalRouter: RoutingRelation, nEscapeChannels: Int = 1) = {
    new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
      if (srcC.src == -1) {
        if (nxtC.vc >= nEscapeChannels) {
          normalRouter(nodeId)(srcC, nxtC.copy(vc=nxtC.vc-nEscapeChannels), pInfo)
        } else {
          escapeRouter(nodeId)(srcC, nxtC, pInfo)
        }
      } else if (nxtC.vc < nEscapeChannels) {
        escapeRouter(nodeId)(srcC, nxtC, pInfo)
      } else if (srcC.vc >= nEscapeChannels && nxtC.vc >= nEscapeChannels) {
        normalRouter(nodeId)(srcC.copy(vc=srcC.vc-nEscapeChannels), nxtC.copy(vc=nxtC.vc-nEscapeChannels), pInfo)
      } else {
        false
    }
    }, (c, v) => {
      c.isIngress || c.isEgress || c.vc < nEscapeChannels
    })
  }

  def noRoutingAtEgress = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => pInfo.dst != nodeId)


  // Usable policies
  val allLegal = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => true)

  val bidirectionalLine = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    if (nodeId < nxtC.dst) pInfo.dst >= nxtC.dst else pInfo.dst <= nxtC.dst
  }) && noRoutingAtEgress

  def unidirectionalTorus1DDateline(nNodes: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    // (if (srcIsIngress(nodeId)(srcC, nxtC, pInfo)) {
    //   !nxtIsVC0
    // } else if (srcIsVC0(nodeId)(srcC, nxtC, pInfo)) {
    //   nxtIsVC0
    // } else if (nodeId == nNodes - 1) {
    //   nxtVLTSrcV
    // } else {
    //   nxtVLESrcV && !nxtIsVC0
    // })(nodeId)(srcC, nxtC, pInfo)

    if (srcC.src == -1)  {
      nxtC.vc != 0
    } else if (srcC.vc == 0) {
      nxtC.vc == 0
    } else if (nodeId == nNodes - 1) {
      nxtC.vc < srcC.vc
    } else {
      nxtC.vc <= srcC.vc && nxtC.vc != 0
    }
  }) && noRoutingAtEgress



  def bidirectionalTorus1DDateline(nNodes: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    if (srcC.src == -1)  {
      nxtC.vc != 0
    } else if (srcC.vc == 0) {
      nxtC.vc == 0
    } else if ((nxtC.dst + nNodes - nodeId) % nNodes == 1) {
      if (nodeId == nNodes - 1) {
        nxtC.vc < srcC.vc
      } else {
        nxtC.vc <= srcC.vc && nxtC.vc != 0
      }
    } else if ((nodeId + nNodes - nxtC.dst) % nNodes == 1) {
      if (nodeId == 0) {
        nxtC.vc < srcC.vc
      } else {
        nxtC.vc <= srcC.vc && nxtC.vc != 0
      }
    } else {
      false
    }
  })

  def bidirectionalTorus1DShortest(nNodes: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val cwDist = (pInfo.dst + nNodes - nodeId) % nNodes
    val ccwDist = (nodeId + nNodes - pInfo.dst) % nNodes
    val distSel = if (cwDist < ccwDist) {
      (nxtC.dst + nNodes - nodeId) % nNodes == 1
    } else if (cwDist > ccwDist) {
      (nodeId + nNodes - nxtC.dst) % nNodes == 1
    } else {
      true
    }
    distSel && bidirectionalTorus1DDateline(nNodes)(nodeId)(srcC, nxtC, pInfo)
  }) && noRoutingAtEgress

  def bidirectionalTorus1DRandom(nNodes: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val sel = if (srcC.src == -1) {
      true
    } else if ((nodeId + nNodes - srcC.src) % nNodes == 1) {
      (nxtC.dst + nNodes - nodeId) % nNodes == 1
    } else {
      (nodeId + nNodes - nxtC.dst) % nNodes == 1
    }
    sel && bidirectionalTorus1DDateline(nNodes)(nodeId)(srcC, nxtC, pInfo)
  }) && noRoutingAtEgress

  def butterfly(kAry: Int, nFly: Int) = {
    require(kAry >= 2 && nFly >= 2)
    val height = pow(kAry, nFly-1).toInt
    def digitsToNum(dig: Seq[Int]) = dig.zipWithIndex.map { case (d,i) => d * pow(kAry,i).toInt }.sum
    val table = (0 until pow(kAry, nFly).toInt).map { i =>
      (0 until nFly).map { n => (i / pow(kAry, n).toInt) % kAry }
    }
    val channels = (1 until nFly).map { i =>
      table.map { e => (digitsToNum(e.drop(1)), digitsToNum(e.updated(i, e(0)).drop(1))) }
    }

    new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
      val (nxtX, nxtY) = (nxtC.dst / height, nxtC.dst % height)
      val (nodeX, nodeY) = (nodeId / height, nodeId % height)
      val (dstX, dstY) = (pInfo.dst / height, pInfo.dst % height)
      if (dstX <= nodeX) {
        false
      } else if (nodeX == nFly-1) {
        true
      } else {
        val dsts = (nxtX until nFly-1).foldRight((0 until height).map { i => Seq(i) }) {
          case (i,l) => (0 until height).map { s => channels(i).filter(_._1 == s).map { case (_,d) =>
            l(d)
          }.flatten }
        }
        dsts(nxtY).contains(pInfo.dst % height)
      }
    })
  }


  def mesh2DDimensionOrdered(firstDim: Int = 0)(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)

    if (firstDim == 0) {
      if (dstX != nodeX) {
        (if (nodeX < nxtX) dstX >= nxtX else dstX <= nxtX) && nxtY == nodeY
      } else {
        (if (nodeY < nxtY) dstY >= nxtY else dstY <= nxtY) && nxtX == nodeX
      }
    } else {
      if (dstY != nodeY) {
        (if (nodeY < nxtY) dstY >= nxtY else dstY <= nxtY) && nxtX == nodeX
      } else {
        (if (nodeX < nxtX) dstX >= nxtX else dstX <= nxtX) && nxtY == nodeY
      }
    }
  })

  // WARNING: Not deadlock free
  def mesh2DMinimal(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX, pInfo.dst / nX)

    val xR = (if (nodeX < nxtX) dstX >= nxtX else if (nodeX > nxtX) dstX <= nxtX else nodeX == nxtX)
    val yR = (if (nodeY < nxtY) dstY >= nxtY else if (nodeY > nxtY) dstY <= nxtY else nodeY == nxtY)
    xR && yR
  })


  def mesh2DWestFirst(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)

    (if (dstX < nodeX) {
      new RoutingRelation((nodeId, srcC, nxtC, pInfo) => nxtX == nodeX - 1)
    } else {
      mesh2DMinimal(nX, nY)
    })(nodeId)(srcC, nxtC, pInfo)
  })

  def mesh2DNorthLast(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)

    (if (dstY > nodeY && dstX != nodeX) {
      mesh2DMinimal(nX, nY) && nxtY != nodeY + 1
    } else if (dstY > nodeY) {
      new RoutingRelation((nodeId, srcC, nxtC, pInfo) => nxtY == nodeY + 1)
    } else {
      mesh2DMinimal(nX, nY)
    })(nodeId)(srcC, nxtC, pInfo)
  })



  def mesh2DAlternatingDimensionOrdered(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)
    val (srcX, srcY)   = (srcC.src % nX , srcC.src / nX)

    val turn = nxtX != srcX && nxtY != srcY
    val canRouteThis = mesh2DDimensionOrdered(srcC.vc % 2)(nX, nY)
    val canRouteNext = mesh2DDimensionOrdered(nxtC.vc % 2)(nX, nY)

    val sel = if (srcC.src == -1) {
      canRouteNext
    } else {
      (canRouteThis && nxtC.vc % 2 == srcC.vc % 2 && nxtC.vc <= srcC.vc) || (canRouteNext && nxtC.vc % 2 != srcC.vc % 2 && nxtC.vc <= srcC.vc)
    }
    (mesh2DMinimal(nX, nY) && sel)(nodeId)(srcC, nxtC, pInfo)
  }) && noRoutingAtEgress


  def mesh2DEscapeRouter(nX: Int, nY: Int) = escapeChannels(mesh2DDimensionOrdered()(nX, nY), mesh2DMinimal(nX, nY))

  def unidirectionalTorus2DDateline(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)
    val (srcX, srcY)   = (srcC.src % nX , srcC.src / nX)

    val turn = nxtX != srcX && nxtY != srcY
    if (srcC.src == -1 || turn) {
      nxtC.vc != 0
    } else if (srcX == nxtX) {
      unidirectionalTorus1DDateline(nY)(nodeY)(
        srcC.copy(src=srcY, dst=nodeY),
        nxtC.copy(src=nodeY, dst=nxtY),
        pInfo.copy(dst=dstY)
      )
    } else if (srcY == nxtY) {
      unidirectionalTorus1DDateline(nX)(nodeX)(
        srcC.copy(src=srcX, dst=nodeX),
        nxtC.copy(src=nodeX, dst=nxtX),
        pInfo.copy(dst=dstX)
      )
    } else {
      false
    }
  })

  def bidirectionalTorus2DDateline(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)
    val (srcX, srcY)   = (srcC.src % nX , srcC.src / nX)

    if (srcC.src == -1) {
      nxtC.vc != 0
    } else if (nodeX == nxtX) {
      bidirectionalTorus1DDateline(nY)(nodeY)(
        srcC.copy(src=srcY, dst=nodeY),
        nxtC.copy(src=nodeY, dst=nxtY),
        pInfo.copy(dst=dstY)
      )
    } else if (nodeY == nxtY) {
      bidirectionalTorus1DDateline(nX)(nodeX)(
        srcC.copy(src=srcX, dst=nodeX),
        nxtC.copy(src=nodeX, dst=nxtX),
        pInfo.copy(dst=dstX)
      )
    } else {
      false
    }
  })



  def dimensionOrderedUnidirectionalTorus2DDateline(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)
    val (srcX, srcY)   = (srcC.src % nX , srcC.src / nX)

    def sel = if (dstX != nodeX) {
      nxtY == nodeY
    } else {
      nxtX == nodeX
    }
    (unidirectionalTorus2DDateline(nX, nY) && sel)(nodeId)(srcC, nxtC, pInfo)
  })

  def dimensionOrderedBidirectionalTorus2DDateline(nX: Int, nY: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val (nxtX, nxtY)   = (nxtC.dst % nX , nxtC.dst / nX)
    val (nodeX, nodeY) = (nodeId % nX, nodeId / nX)
    val (dstX, dstY)   = (pInfo.dst % nX , pInfo.dst / nX)
    val (srcX, srcY)   = (srcC.src % nX , srcC.src / nX)

    val xdir = bidirectionalTorus1DShortest(nX)(nodeX)(
      srcC.copy(src=(if (srcC.src == -1) -1 else srcX), dst=nodeX),
      nxtC.copy(src=nodeX, dst=nxtX),
      pInfo.copy(dst=dstX)
    )
    val ydir = bidirectionalTorus1DShortest(nY)(nodeY)(
      srcC.copy(src=(if (srcC.src == -1) -1 else srcY), dst=nodeY),
      nxtC.copy(src=nodeY, dst=nxtY),
      pInfo.copy(dst=dstY)
    )

    val base = bidirectionalTorus2DDateline(nX, nY)(nodeId)(srcC, nxtC, pInfo)
    val sel = if (dstX != nodeX) xdir else ydir

    sel && base
  })


  // The below tables implement support for virtual subnetworks in a variety of ways
  // NOTE: The topology must have sufficient virtual channels for these to work correctly
  // TODO: Write assertions to check this

  // Independent virtual subnets with no resource sharing
  def nonblockingVirtualSubnetworks(f: RoutingRelation, n: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    (nxtC.vc % n == pInfo.vNet) && f(nodeId)(
      srcC.copy(vc=srcC.vc / n),
      nxtC.copy(vc=nxtC.vc / n),
      pInfo.copy(vNet=0)
    )
  }, (c, v) => {
    f.isEscape(c.copy(vc=c.vc / n), 0)
  })

  // Virtual subnets with 1 dedicated virtual channel each, and some number of shared channels
  def sharedNonblockingVirtualSubnetworks(f: RoutingRelation, n: Int, nSharedChannels: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    def trueVIdToVirtualVId(vId: Int) = if (vId < n) 0 else vId - n
    if (nxtC.vc < n) {
      nxtC.vc == pInfo.vNet && f(nodeId)(
        srcC.copy(vc=trueVIdToVirtualVId(srcC.vc)),
        nxtC.copy(vc=0),
        pInfo.copy(vNet=0)
      )
    } else {
      f(nodeId)(
        srcC.copy(vc=trueVIdToVirtualVId(srcC.vc)),
        nxtC.copy(vc=trueVIdToVirtualVId(nxtC.vc)),
        pInfo.copy(vNet=0)
      )
    }
  }, (c, v) => {
    def trueVIdToVirtualVId(vId: Int) = if (vId < n) 0 else vId - n
    f.isEscape(c.copy(vc=trueVIdToVirtualVId(c.vc)), 0)
  })

  def blockingVirtualSubnetworks(f: RoutingRelation, n: Int) = new RoutingRelation((nodeId, srcC, nxtC, pInfo) => {
    val lNxtV = nxtC.vc - pInfo.vNet
    if (lNxtV < 0) {
      false
    } else {
      f(nodeId)(srcC, nxtC.copy(vc=lNxtV), pInfo.copy(vNet=0))
    }
  }, (c, v) => {
    c.vc >= v && f.isEscape(c.copy(vc=c.vc - v), 0)
  })
}