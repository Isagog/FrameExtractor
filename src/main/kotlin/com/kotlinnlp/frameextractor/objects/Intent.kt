/* Copyright 2018-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.frameextractor.objects

import com.beust.klaxon.JsonObject
import com.beust.klaxon.json
import java.io.Serializable

/**
 * An intent.
 *
 * @property name the intent name
 * @property slots the list of slots of the intent
 * @property score the intent score (of prediction)
 */
data class Intent(val name: String, val slots: List<Slot>, val score: Double) {

  /**
   * The configuration of an [Intent].
   * It describes its name and its possible slots.
   *
   * @property name the intent name
   * @param slots the list of all the possible slot names that can be associated to this intent
   */
  class Configuration(val name: String, slots: List<String>) : Serializable {

    companion object {

      /**
       * Private val used to serialize the class (needed by Serializable).
       */
      @Suppress("unused")
      private const val serialVersionUID: Long = 1L

      /**
       * The name used to generate the slot for tokens that actually do not represent a slot of the intent.
       */
      const val NO_SLOT_NAME = "NoSlot"
    }

    /**
     * The list of all the possible slot names that can be associated to this intent, including the 'no-slot'.
     */
    val slots: List<String> = (slots.toSet() + NO_SLOT_NAME).toList()

    /**
     * @param slotName the name of a possible slot of this intent
     *
     * @return the index of the slot with the given name within the possible slots defined in this configuration
     */
    fun getSlotIndex(slotName: String): Int = this.slots.indexOfFirst { it == slotName }

    /**
     * @param other any object
     *
     * @return if this configuration is equal to the given object
     */
    override fun equals(other: Any?): Boolean =
      other is Configuration && this.name == other.name && this.slots == other.slots

    /**
     * @return the hash code of this configuration
     */
    override fun hashCode(): Int = 31 * this.name.hashCode() + this.slots.hashCode()
  }

  /**
   * @param tokenForms the list of token forms of the input sentence
   *
   * @return the JSON representation of this intent
   */
  fun toJSON(tokenForms: List<String>): JsonObject = json {
    obj(
      "name" to this@Intent.name,
      "slots" to array(this@Intent.slots.map { it.toJSON(tokenForms) }),
      "score" to this@Intent.score
    )
  }
}
