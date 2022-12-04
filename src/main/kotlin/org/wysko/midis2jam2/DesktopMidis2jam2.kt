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
package org.wysko.midis2jam2

import com.jme3.app.Application
import com.jme3.app.state.AppStateManager
import com.jme3.input.KeyInput
import com.jme3.input.controls.KeyTrigger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.wysko.midis2jam2.gui.ConfigureBackground
import org.wysko.midis2jam2.midi.MidiFile
import org.wysko.midis2jam2.util.Utils
import org.wysko.midis2jam2.world.BackgroundController
import org.wysko.midis2jam2.world.CameraAngle.Companion.preventCameraFromLeaving
import java.util.Properties
import java.util.Timer
import java.util.TimerTask
import javax.sound.midi.Sequencer

/**
 * Contains all the code relevant to operating the 3D scene.
 */
class DesktopMidis2jam2(
    /** The MIDI sequencer. */
    private val sequencer: Sequencer,

    /** The MIDI file to play. */
    midiFile: MidiFile,

    /** The settings to use. */
    properties: Properties,

    /** Callback if midis2jam2 closes unexpectedly. */
    private val onClose: () -> Unit
) : Midis2jam2(midiFile, properties) {

    private var sequencerStarted: Boolean = false
    private var skippedTicks = 0
    private var initiatedFadeIn = false
    private val midiFileMicrosecondLength by lazy { sequencer.microsecondLength }

    /**
     * Initializes the application.
     */
    override fun initialize(stateManager: AppStateManager, app: Application) {
        super.initialize(stateManager, app)

        /* Register key map */
        Json.decodeFromString<Array<KeyMap>>(Utils.resourceToString("/keymap.json")).forEach {
            with(app.inputManager) {
                addMapping(it.name, KeyTrigger(KeyInput::class.java.getField(it.key).getInt(KeyInput::class.java)))
                addListener(this@DesktopMidis2jam2, it.name)
            }
        }

        /* To begin MIDI playback, I perform a check every millisecond to see if it is time to begin the playback of
         * the MIDI file. This is done by looking at timeSinceStart which contains the number of seconds since the
         * beginning of the file. It starts as a negative number to represent that time is to pass before the file will
         * play. Once it reaches 0, playback should begin.
         *
         * The Java MIDI sequencer has a bug where the first tempo of the file will not be applied, so once the
         * sequencer is ready to play, we set the tempo. And, sometimes it will miss a tempo change in the file. To
         * reduce the complications from this (unfortunately, it does not solve the issue; it only partially treats it)
         * we perform a check every millisecond and apply any tempos that should be applied now.
         */
        Timer(true).scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    if (timeSinceStart + properties.getProperty("latency_fix").toInt() / 1000.0 >= 0 &&
                        !sequencerStarted && sequencer.isOpen
                    ) {
                        sequencer.tempoInBPM = file.tempos[0].bpm().toFloat()
                        sequencer.start()
                        sequencerStarted = true
                        Timer(true).scheduleAtFixedRate(
                            object : TimerTask() {
                                val tempos = ArrayList(file.tempos)
                                override fun run() {
                                    while (tempos.isNotEmpty() && tempos.first().time < sequencer.tickPosition) {
                                        sequencer.tempoInBPM = tempos.removeAt(0).bpm().toFloat()
                                    }
                                }
                            },
                            0,
                            1
                        )
                    }
                }
            },
            0,
            1
        )

        /*** LOAD SKYBOX ***/
        BackgroundController.configureBackground(
            ConfigureBackground.type(),
            ConfigureBackground.value(),
            this,
            rootNode
        )
    }

    /**
     * Cleans up the application.
     */
    override fun cleanup() {
        sequencer.run {
            stop()
            close()
        }
        onClose()
    }

    /**
     * Performs a tick.
     */
    override fun update(tpf: Float) {
        super.update(tpf)

        if (!initiatedFadeIn && timeSinceStart > -1.5) {
            fade.fadeIn()
            initiatedFadeIn = true
        }

        instruments.forEach {
            /* Null if not implemented yet */
            it.tick(timeSinceStart, tpf)
        }

        /* If at the end of the file */
        if (timeSinceStart >= file.length) {
            if (!afterEnd) {
                stopTime = timeSinceStart
            }
            afterEnd = true
        }

        /* If after the end, by three seconds */
        if (afterEnd && timeSinceStart >= stopTime + 3.0) {
            exit()
        }

        shadowController?.tick()
        standController.tick()
        lyricController.tick(timeSinceStart)
        autocamController.tick(timeSinceStart, tpf)
        hudController.tick(timeSinceStart, fade.value)
        preventCameraFromLeaving(app.camera)

        /* This is a hack to prevent the first few frames from updating the timeSinceStart variable. */
        if (skippedTicks++ < 3) {
            return
        }

        if (sequencer.isOpen && !paused) {
            /* Increment time if sequencer is ready / playing */
            timeSinceStart += tpf.toDouble()
        }

//        rootNode.breadthFirstTraversal { it.cullHint = Spatial.CullHint.Never }
    }

    override fun seek(time: Double) {
        timeSinceStart = time
        sequencer.microsecondPosition = (time * 1E6).toLong()
        eventCollectors.forEach { it.seek(time) }
        notePeriodCollectors.forEach { it.seek(time) }
    }

    /**
     * Stops the app state.
     */
    override fun exit() {
        if (sequencer.isOpen) {
            sequencer.stop()
        }
        app.stateManager.detach(this)
        app.stop()
        onClose()
    }

    override fun togglePause() {
        super.togglePause()

        if (paused) sequencer.stop() else sequencer.start()
    }
}

/**
 * Stores a single map between an action name and a key.
 */
@Serializable
private data class KeyMap(
    /**
     * The name of the action.
     */
    val name: String,

    /**
     * The name of the key, as defined in [KeyInput].
     */
    val key: String
)
