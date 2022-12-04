/*
 * Copyright (C) 2022 Jacob Wysko
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
package org.wysko.midis2jam2.instrument.family.reed

import com.jme3.math.Quaternion
import com.jme3.scene.Spatial
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.algorithmic.BellStretcher
import org.wysko.midis2jam2.instrument.algorithmic.HandPositionFingeringManager
import org.wysko.midis2jam2.instrument.algorithmic.HandPositionFingeringManager.Companion.from
import org.wysko.midis2jam2.instrument.algorithmic.StandardBellStretcher
import org.wysko.midis2jam2.instrument.clone.ClonePitchBendConfiguration
import org.wysko.midis2jam2.instrument.clone.HandedClone
import org.wysko.midis2jam2.instrument.family.pipe.HandedInstrument
import org.wysko.midis2jam2.midi.MidiChannelSpecificEvent
import org.wysko.midis2jam2.midi.MidiNoteEvent
import org.wysko.midis2jam2.util.Utils.rad
import org.wysko.midis2jam2.world.Axis

/** The Clarinet fingering manager. */
private val FINGERING_MANAGER: HandPositionFingeringManager = from(Clarinet::class.java)

/**
 * The clarinet has both hand positions and a stretchy bell.
 *
 * [Fingering chart](https://bit.ly/34Quj4e)
 */
class Clarinet(context: Midis2jam2, eventList: List<MidiChannelSpecificEvent>) :
    HandedInstrument(
        context,
        /* Strip notes outside of standard range */
        eventList.filterIsInstance<MidiNoteEvent>().filter { it.note in 50..90 }
            .plus(eventList.filter { it !is MidiNoteEvent }),
        ClarinetClone::class.java,
        FINGERING_MANAGER
    ) {

    override val pitchBendConfiguration: ClonePitchBendConfiguration = ClonePitchBendConfiguration(reversed = true)

    override fun moveForMultiChannel(delta: Float) {
        offsetNode.setLocalTranslation(0f, 20 * updateInstrumentIndex(delta), 0f)
    }

    /** The type Clarinet clone. */
    inner class ClarinetClone : HandedClone(this@Clarinet, 0.075f) {

        override val leftHands: Array<Spatial> = Array(19) {
            parent.context.loadModel("ClarinetLeftHand$it.obj", "hands.bmp")
        }

        override val rightHands: Array<Spatial> = Array(13) {
            parent.context.loadModel("ClarinetRightHand$it.obj", "hands.bmp")
        }

        init {
            /* Load body */
            modelNode.attachChild(context.loadModel("ClarinetBody.obj", "ClarinetSkin.png"))

            /* Position Clarinet */
            animNode.setLocalTranslation(0f, 0f, 10f)
            highestLevel.localRotation = Quaternion().fromAngles(rad(-25.0), rad(45.0), 0f)

            loadHands()
        }

        /** The bell. */
        private val bell: Spatial = context.loadModel("ClarinetHorn.obj", "ClarinetSkin.png").apply {
            modelNode.attachChild(this)
            setLocalTranslation(0f, -20.7125f, 0f)
        }

        /** The bell stretcher. */
        private val bellStretcher: BellStretcher = StandardBellStretcher(0.7f, Axis.Y, bell)

        override fun moveForPolyphony() {
            offsetNode.localRotation = Quaternion().fromAngles(0f, rad((25 * indexForMoving()).toDouble()), 0f)
        }

        override fun tick(time: Double, delta: Float) {
            super.tick(time, delta)

            /* Stretch bell */
            bellStretcher.tick(currentNotePeriod, time)
        }

        override fun toString(): String {
            return super.toString() + buildString {
                append(debugProperty("stretch", (bellStretcher as StandardBellStretcher).scale))
            }
        }
    }

    init {
        instrumentNode.setLocalTranslation(-25f, 50f, 0f)
        instrumentNode.setLocalScale(0.9f)
    }
}
