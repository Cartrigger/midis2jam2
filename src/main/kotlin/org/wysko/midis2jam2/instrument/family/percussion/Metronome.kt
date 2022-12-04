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
import org.wysko.midis2jam2.instrument.algorithmic.Striker
import org.wysko.midis2jam2.instrument.family.percussion.drumset.NonDrumSetPercussion
import org.wysko.midis2jam2.midi.METRONOME_BELL
import org.wysko.midis2jam2.midi.METRONOME_CLICK
import org.wysko.midis2jam2.midi.MidiNoteOnEvent
import org.wysko.midis2jam2.util.Utils.rad

/**
 * The Metronome.
 */
class Metronome(context: Midis2jam2, hits: MutableList<MidiNoteOnEvent>) : NonDrumSetPercussion(context, hits) {

    private val bellStriker = Striker(
        context = context,
        strikeEvents = hits.filter { it.note == METRONOME_BELL },
        strikeSpeed = 2.4,
        maxIdleAngle = 30.0,
        stickModel = Node() // Dummy note, rotation will be copied in [tick]
    )

    private val clickStriker = Striker(
        context = context,
        strikeEvents = hits.filter { it.note == METRONOME_CLICK },
        strikeSpeed = 2.4,
        maxIdleAngle = 30.0,
        stickModel = Node() // Dummy note, rotation will be copied in [tick]
    )

    private val bell = context.loadModel("MetronomePendjulum2.obj", "HornSkin.bmp").apply {
        instrumentNode.attachChild(this)
        setLocalTranslation(0f, 0f, 0.5f)
    }
    private val click = context.loadModel("MetronomePendjulum1.obj", "ShinySilver.bmp").apply {
        instrumentNode.attachChild(this)
        setLocalTranslation(0f, 0f, 1f)
    }

    private var clickSwingsRight = true
    private var bellSwingsRight = true

    private var previousBellTarget: MidiNoteOnEvent? = null
    private var previousClickTarget: MidiNoteOnEvent? = null

    init {
        instrumentNode.apply {
            attachChild(context.loadModel("MetronomeBox.obj", "Wood.bmp"))
            setLocalTranslation(-20f, 0f, -46f)
            localRotation = Quaternion().fromAngles(0f, rad(20.0), 0f)
        }
    }

    @Suppress("DuplicatedCode")
    override fun tick(time: Double, delta: Float) {
        super.tick(time, delta)

        val bellResults = bellStriker.tick(time, delta)
        val clickResults = clickStriker.tick(time, delta)

        // Bell
        bellResults.strikingFor?.let {
            if (it != previousBellTarget) {
                previousBellTarget = it
                bellSwingsRight = !bellSwingsRight // Alternate if swinging for a new note
            }
        }
        val bellRotation = when {
            bellSwingsRight -> bellResults.rotationAngle - rad(30.0)
            else -> -bellResults.rotationAngle + rad(30.0)
        }
        bell.localRotation = Quaternion().fromAngles(0f, 0f, bellRotation)

        // Click
        clickResults.strikingFor?.let {
            if (it != previousClickTarget) {
                previousClickTarget = it
                clickSwingsRight = !clickSwingsRight // Alternate if swinging for a new note
            }
        }
        val clickRotation = when {
            clickSwingsRight -> clickResults.rotationAngle - rad(30.0)
            else -> -clickResults.rotationAngle + rad(30.0)
        }
        click.localRotation = Quaternion().fromAngles(0f, 0f, clickRotation)
    }
}
