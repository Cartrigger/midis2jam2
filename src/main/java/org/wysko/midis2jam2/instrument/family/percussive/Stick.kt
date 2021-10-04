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

import com.jme3.math.Quaternion
import com.jme3.scene.Spatial
import com.jme3.scene.Spatial.CullHint
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.algorithmic.NoteQueue
import org.wysko.midis2jam2.midi.MidiNoteOnEvent
import org.wysko.midis2jam2.util.Utils.rad
import org.wysko.midis2jam2.world.Axis

/**
 * Contains logic for animating stick strikes.
 */
object Stick {
    /** The standard speed at which a stick strikes. */
    const val STRIKE_SPEED: Double = 4.0

    /** The standard max angle at which a stick bends back to. */
    const val MAX_ANGLE: Double = 50.0

    private fun proposedRotation(
        context: Midis2jam2,
        time: Double,
        nextHit: MidiNoteOnEvent?,
        maxAngle: Double,
        strikeSpeed: Double
    ): Double {
        return if (nextHit == null) maxAngle + 1
        else -1000 * (6E7 / context.file.tempoBefore(nextHit).number / (1000f / strikeSpeed)) * (time - context.file.eventInSeconds(
            nextHit
        ))
    }

    /**
     * Calculates the desired rotation and visibility of a stick at any given point.
     *
     * @param context     context to midis2jam2
     * @param stickNode   the node that will rotate and cull to move the stick
     * @param time        the current time, in seconds
     * @param delta       the amount of time since the last frame
     * @param strikes     the list of strikes this stick is responsible for
     * @param strikeSpeed the speed at which to strike
     * @param maxAngle    the maximum angle to hold the stick at
     * @param axis        the axis on which to rotate the stick
     * @return a [StickStatus] describing the current status of the stick
     */
    @SuppressWarnings("kotlin:S107")
    fun handleStick(
        context: Midis2jam2,
        stickNode: Spatial,
        time: Double,
        delta: Float,
        strikes: MutableList<MidiNoteOnEvent>,
        strikeSpeed: Double = STRIKE_SPEED,
        maxAngle: Double = MAX_ANGLE,
        axis: Axis = Axis.X
    ): StickStatus {
        val rotComp = when (axis) {
            Axis.X -> 0
            Axis.Y -> 1
            Axis.Z -> 2
        }

        val nextHit = NoteQueue.collect(strikes, context, time).lastOrNull()
        val strikeNow = nextHit != null && context.file.eventInSeconds(nextHit) <= time
        val proposedRotation = proposedRotation(context, time, nextHit, maxAngle, strikeSpeed)
        val floats = stickNode.localRotation.toAngles(FloatArray(3))

        if (proposedRotation > maxAngle) {
            // Not yet ready to strike
            if (floats[rotComp] <= maxAngle) {
                // We have come down, need to recoil
                val angle = rad(maxAngle).coerceAtMost(floats[rotComp] + 5f * delta)
                when (axis) {
                    Axis.X -> {
                        stickNode.localRotation = Quaternion().fromAngles(angle, 0f, 0f)
                    }
                    Axis.Y -> {
                        stickNode.localRotation = Quaternion().fromAngles(0f, angle, 0f)
                    }
                    Axis.Z -> {
                        stickNode.localRotation = Quaternion().fromAngles(0f, 0f, angle)
                    }
                }
            }
        } else {
            // Striking
            val angle = 0.0.coerceAtLeast(maxAngle.coerceAtMost(proposedRotation))
            when (axis) {
                Axis.X -> {
                    stickNode.localRotation = Quaternion().fromAngles(rad(angle), 0f, 0f)
                }
                Axis.Y -> {
                    stickNode.localRotation = Quaternion().fromAngles(0f, rad(angle), 0f)
                }
                Axis.Z -> {
                    stickNode.localRotation = Quaternion().fromAngles(0f, 0f, rad(angle))
                }
            }
        }

        val finalAngles = stickNode.localRotation.toAngles(FloatArray(3))
        if (finalAngles[rotComp] >= rad(maxAngle)) {
            // Not yet ready to strike
            stickNode.cullHint = CullHint.Always
        } else {
            // Striking or recoiling
            stickNode.cullHint = CullHint.Dynamic
        }
        return StickStatus(
            if (strikeNow) nextHit else null,
            finalAngles[rotComp], if (proposedRotation > maxAngle) null else nextHit
        )
    }

    /** Reports information about what has happened when [handleStick] has been called. */
    class StickStatus(
        /** The strike that the stick just played, or null if it didn't. */
        val strike: MidiNoteOnEvent?,

        /** The angle of rotation. */
        val rotationAngle: Float,

        /** The note this stick is striking for. */
        private val strikingFor: MidiNoteOnEvent?
    ) {

        /**
         * Did the stick just strike?
         *
         * @return true if the stick just struck, false otherwise
         */
        fun justStruck(): Boolean {
            return strike != null
        }

        /**
         * Returns the [MidiNoteOnEvent] that this sticking is striking for.
         *
         * @return the MIDI note this stick is currently striking for
         */
        fun strikingFor(): MidiNoteOnEvent? {
            return strikingFor
        }
    }
}