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
package org.wysko.midis2jam2.instrument.family.chromaticpercussion

import com.jme3.math.ColorRGBA
import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import com.jme3.scene.Geometry
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.DecayedInstrument
import org.wysko.midis2jam2.instrument.algorithmic.EventCollector
import org.wysko.midis2jam2.instrument.family.percussive.TwelveDrumOctave.TwelfthOfOctaveDecayed
import org.wysko.midis2jam2.midi.MidiChannelSpecificEvent
import org.wysko.midis2jam2.world.GlowController

/** Texture file for shiny silver. */
const val SHINY_SILVER: String = "ShinySilver.bmp"

private val OFFSET_DIRECTION_VECTOR = Vector3f(0f, 0f, -18f)

/**
 * The music box has several animation components. The first is the spindle/cylinder. The spindle spins at a rate of 1/4
 * turn per beat = π/2 rad. To calculate this, the spindle is rotated by `0.5 * PI * delta * (6E7 / bpm) / 60` on
 * each frame.
 */
class MusicBox(context: Midis2jam2, eventList: List<MidiChannelSpecificEvent>) : DecayedInstrument(context, eventList) {

    /** Each of the hanging notes. */
    private val notes = Array(12) { i ->
        OneMusicBoxNote(i).apply {
            instrumentNode.attachChild(highestLevel)
        }
    }

    /** Contains the spindle. */
    private val cylinder = Node()

    /** List of points that are currently active. */
    private val points: MutableList<Spatial> = ArrayList()

    /** Keeps track of how many radians each point has rotated. */
    private val pointRotations = HashMap<Spatial, Float>()

    /** Model of the music box point. */
    private val pointModel: Spatial

    /**
     * Contains a pool of spatials that are music box notes. This is so that a new music box note doesn't have to be
     * spawned every time there is a new note.
     */
    private val pool: MutableList<Spatial> = ArrayList()

    private val pointsHits = EventCollector(hits, context) { event, time ->
        context.file.tickToSeconds(event.time - context.file.division) <= time // Quarter note early
    }

    private val notesHits = EventCollector(hits, context)

    override fun tick(time: Double, delta: Float) {
        super.tick(time, delta)
        rotateCylinder(time, delta)

        pointsHits.advanceCollectAll(time).forEach {
            // Handle points
            with(if (pool.isNotEmpty()) pool.removeAt(0) else pointModel.clone()) {
                instrumentNode.attachChild(this)
                localRotation = Quaternion().fromAngles((-Math.PI / 2).toFloat(), 0f, 0f)
                points.add(this)
                pointRotations[this] = 0f
                setLocalTranslation((it.note + 3) % 12 - 5.5f, 0f, 0f)
            }
        }

        notesHits.advanceCollectAll(time).forEach {
            notes[(it.note + 3) % 12].play()
        }

        points.filter { pointRotations[it]!! > 4.71 }
            .onEach { instrumentNode.detachChild(it) } // Hide
            .also { points.removeAll(it) } // No longer active
            .also { pool.addAll(it) } // Put back in pool

        /* Tick the hanging notes */
        notes.forEach { it.tick(delta) }
    }

    /**
     * Rotates the cylinder. The cylinder rotates PI/2 radians for every quarter note.
     *
     * @param delta the amount of time since the last frame
     */
    private fun rotateCylinder(time: Double, delta: Float) {
        val tempo = context.file.tempoAt(time).number

        /* Calculate rotation angle */
        val xAngle = (0.5 * Math.PI * delta * (6E7 / tempo) / 60.0).toFloat()

        /* Rotate each point */
        pointRotations.entries.forEach {
            it.key.rotate(xAngle, 0f, 0f)
            pointRotations[it.key] = it.value + xAngle
        }

        /* Rotate cylinder */
        cylinder.rotate(xAngle, 0f, 0f)
    }

    override fun moveForMultiChannel(delta: Float) {
        offsetNode.localTranslation = OFFSET_DIRECTION_VECTOR.mult(updateInstrumentIndex(delta))
    }

    /** A single music box note. */
    inner class OneMusicBoxNote(i: Int) : TwelfthOfOctaveDecayed() {

        /** The hanging key. */
        private val key: Spatial = context.loadModel("MusicBoxKey.obj", SHINY_SILVER, 0.9f).apply {
            highestLevel.attachChild(this)
            setLocalTranslation(i - 5.5f, 7f, 0f)
            localScale = Vector3f(-0.0454f * i + 1, 1f, 1f)
        }

        /** The animation progress. */
        private var progress = 0.0

        /** True if this note is recoiling, false otherwise. */
        private var playing = false

        private val glowController = GlowController()

        /** Call to begin recoiling. */
        fun play() {
            playing = true
            progress = 0.0
        }

        override fun tick(delta: Float) {
            /* Update progress */
            if (playing) {
                progress += (delta * 4).toDouble()
                (key as Geometry).material.setColor("GlowColor", glowController.calculate(progress))
            } else {
                (key as Geometry).material.setColor("GlowColor", ColorRGBA.Black)
            }

            /* Animation is finished */
            if (progress >= 1) {
                progress = 0.0
                playing = false
            }

            /* Update animation */
            key.localRotation = Quaternion().fromAngles(rotationFactorFromProgress(), 0f, 0f)
        }

        /** Calculates the rotation factor from the current animation progress. */
        private fun rotationFactorFromProgress(): Float {
            val rotation = if (progress <= 0.5) progress.toFloat() else (-progress + 1).toFloat()
            return -rotation
        }
    }

    init {

        instrumentNode.run {
            attachChild(context.loadModel("MusicBoxCase.obj", "Wood.bmp"))
            attachChild(context.loadModel("MusicBoxTopBlade.obj", SHINY_SILVER, 0.9f))
            attachChild(cylinder)
            setLocalTranslation(37f, 5f, -5f)
        }
        cylinder.attachChild(context.loadModel("MusicBoxSpindle.obj", SHINY_SILVER, 0.9f))
        pointModel = context.loadModel("MusicBoxPoint.obj", SHINY_SILVER, 0.9f)
    }
}
