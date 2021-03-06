/*
 * Copyright (c) bdew, 2014 - 2015
 * https://github.com/bdew/ae2stuff
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.ae2stuff.machines.wireless

import java.util

import appeng.api.AEApi
import appeng.api.networking.{GridFlags, IGridConnection}
import net.bdew.ae2stuff.AE2Stuff
import net.bdew.ae2stuff.grid.{GridTile, VariableIdlePower}
import net.bdew.lib.PimpVanilla._
import net.bdew.lib.data.base.{TileDataSlots, UpdateKind}
import net.bdew.lib.multiblock.data.DataSlotPos
import net.minecraft.block.state.IBlockState
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class TileWireless extends TileDataSlots with GridTile with VariableIdlePower {
  val cfg = MachineWireless

  val link = DataSlotPos("link", this).setUpdate(UpdateKind.SAVE, UpdateKind.WORLD)

  var connection: IGridConnection = null

  def isLinked = link.isDefined
  def getLink = link flatMap worldObj.getTileSafe[TileWireless]

  override def getFlags = util.EnumSet.of(GridFlags.DENSE_CAPACITY)

  serverTick.listen(() => {
    if (connection == null && link.isDefined) {
      setupConnection()
    }
  })

  def doLink(other: TileWireless): Boolean = {
    if (other.link.isEmpty) {
      other.link.set(pos)
      link.set(other.getPos)
      setupConnection()
    } else false
  }

  def doUnlink(): Unit = {
    breakConnection()
    getLink foreach { that =>
      this.link := None
      that.link := None
    }
  }

  def setupConnection(): Boolean = {
    getLink foreach { that =>
      try {
        connection = AEApi.instance().createGridConnection(this.getNode, that.getNode)
        that.connection = connection
        val power = cfg.powerBase + cfg.powerDistanceMultiplier * this.pos.distanceSq(that.pos)
        this.setIdlePowerUse(power)
        that.setIdlePowerUse(power)
        BlockWireless.setActive(worldObj, pos, true)
        BlockWireless.setActive(worldObj, that.getPos, true)
        return true
      } catch {
        case t: Exception =>
          AE2Stuff.logWarnException("Failed setting up wireless link %s <-> %s", t, pos, that.getPos)
          doUnlink()
      }
    }
    false
  }

  def breakConnection(): Unit = {
    if (connection != null)
      connection.destroy()
    connection = null
    setIdlePowerUse(0D)
    getLink foreach { other =>
      other.connection = null
      other.setIdlePowerUse(0D)
      BlockWireless.setActive(worldObj, other.getPos, false)
    }
    BlockWireless.setActive(worldObj, pos, false)
  }

  override def getMachineRepresentation: ItemStack = new ItemStack(BlockWireless)

  override def shouldRefresh(world: World, pos: BlockPos, oldState: IBlockState, newSate: IBlockState): Boolean = newSate.getBlock != BlockWireless
}
