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
package org.wysko.midis2jam2.instrument.family.percussion

import com.jme3.math.Quaternion
import com.jme3.scene.Node
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.algorithmic.StickType
import org.wysko.midis2jam2.instrument.algorithmic.Striker
import org.wysko.midis2jam2.instrument.family.percussion.drumset.NonDrumSetPercussion
import org.wysko.midis2jam2.midi.HIGH_TIMBALE
import org.wysko.midis2jam2.midi.LOW_TIMBALE
import org.wysko.midis2jam2.midi.MidiNoteOnEvent
import org.wysko.midis2jam2.util.Utils.rad

/** The Timbales. */
class Timbales(context: Midis2jam2, hits: MutableList<MidiNoteOnEvent>) : NonDrumSetPercussion(context, hits) {

    /** The left timbale anim node. */
    private val lowTimbaleAnimNode = Node().also {
        it.attachChild(context.loadModel("DrumSet_Timbale.obj", "DrumShell_Timbale.bmp"))
    }

    /** The right timbale anim node. */
    private val highTimbaleAnimNode = Node().also {
        it.attachChild(
            context.loadModel("DrumSet_Timbale.obj", "DrumShell_Timbale.bmp").apply {
                scale(0.75f)
            }
        )
    }

    private val lowStick = Striker(
        context = context,
        hits.filter { it.note == LOW_TIMBALE },
        stickModel = StickType.DRUMSET_STICK
    ).apply {
        setParent(lowTimbaleAnimNode)
        node.move(0f, 0f, 10f)
    }

    private val highStick = Striker(
        context = context,
        hits.filter { it.note == HIGH_TIMBALE },
        stickModel = StickType.DRUMSET_STICK
    ).apply {
        setParent(highTimbaleAnimNode)
        node.move(0f, 0f, 10f)
    }

    init {
        // Low timbale node
        Node().apply {
            attachChild(lowTimbaleAnimNode)
            setLocalTranslation(-45.9f, 50.2f, -59.1f)
            localRotation = Quaternion().fromAngles(rad(32.0), rad(56.6), rad(-2.6))

            instrumentNode.attachChild(this)
        }

        // High timbale node
        Node().apply {
            attachChild(highTimbaleAnimNode)
            setLocalTranslation(-39f, 50.1f, -69.7f)
            localRotation = Quaternion().fromAngles(rad(33.8), rad(59.4), rad(-1.8))

            instrumentNode.attachChild(this)
        }
    }

    override fun tick(time: Double, delta: Float) {
        super.tick(time, delta)

        recoilDrum(lowTimbaleAnimNode, lowStick.tick(time, delta).velocity, delta)
        recoilDrum(highTimbaleAnimNode, highStick.tick(time, delta).velocity, delta)
    }
}
