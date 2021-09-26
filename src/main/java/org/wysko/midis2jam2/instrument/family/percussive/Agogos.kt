/*
 * Copyright (C) 2021 Jacob Wysko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */
package org.wysko.midis2jam2.instrument.family.percussive

import com.jme3.math.FastMath
import com.jme3.math.Quaternion
import com.jme3.scene.Node
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.family.percussion.drumset.PercussionInstrument
import org.wysko.midis2jam2.midi.MidiChannelSpecificEvent
import org.wysko.midis2jam2.util.MatType
import org.wysko.midis2jam2.util.Utils.rad

/**
 * The melodic agogos.
 */
class Agogos(
	context: Midis2jam2,
	eventList: List<MidiChannelSpecificEvent>
) : OctavePercussion(context, eventList) {

	override fun tick(time: Double, delta: Float) {
		super.tick(time, delta)
		twelfths.forEach { it!!.tick(delta) }
	}

	override fun moveForMultiChannel(delta: Float) {
		offsetNode.setLocalTranslation(0f, 18 + 3.6f * indexForMoving(delta), 0f)
		instrumentNode.localRotation =
			Quaternion().fromAngles(0f, -FastMath.HALF_PI + FastMath.HALF_PI * indexForMoving(delta), 0f)
	}

	/**
	 * A single agogo.
	 */
	inner class Agogo(i: Int) : TwelfthOfOctaveDecayed() {
		override fun tick(delta: Float) {
			val localTranslation = highestLevel.localTranslation
			if (localTranslation.y < -0.0001) {
				highestLevel.setLocalTranslation(
					0f,
					0f.coerceAtMost(localTranslation.y + PercussionInstrument.DRUM_RECOIL_COMEBACK * delta), 0f
				)
			} else {
				highestLevel.setLocalTranslation(0f, 0f, 0f)
			}
		}

		init {
			val mesh = context.loadModel("AgogoSingle.obj", "HornSkinGrey.bmp", MatType.REFLECTIVE, 0.9f)
			mesh.setLocalScale(1 - 0.036f * i)
			animNode.attachChild(mesh)
		}
	}

	init {
		for (i in 0..11) {
			malletNodes[i] = Node()
			val child = context.loadModel("DrumSet_Stick.obj", "StickSkin.bmp")
			child.setLocalTranslation(0f, 0f, -5f)
			malletNodes[i].setLocalTranslation(0f, 0f, 18f)
			malletNodes[i].attachChild(child)
			val oneBlock = Node()
			oneBlock.attachChild(malletNodes[i])
			val agogo = Agogo(i)
			twelfths[i] = agogo
			oneBlock.attachChild(agogo.highestLevel)
			percussionNodes[i].attachChild(oneBlock)
			oneBlock.setLocalTranslation(0f, 0f, 17f)
			percussionNodes[i].localRotation = Quaternion().fromAngles(0f, rad(7.5 * i), 0f)
			percussionNodes[i].setLocalTranslation(0f, 0.3f * i, 0f)
			instrumentNode.attachChild(percussionNodes[i])
		}
		instrumentNode.setLocalTranslation(75f, 0f, -35f)
	}
}