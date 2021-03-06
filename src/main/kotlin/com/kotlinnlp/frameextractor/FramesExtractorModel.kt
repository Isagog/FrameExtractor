/* Copyright 2018-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.frameextractor

import com.kotlinnlp.frameextractor.objects.Intent
import com.kotlinnlp.simplednn.core.functionalities.activations.ActivationFunction
import com.kotlinnlp.simplednn.core.functionalities.activations.Softmax
import com.kotlinnlp.simplednn.core.functionalities.activations.Tanh
import com.kotlinnlp.simplednn.core.layers.LayerInterface
import com.kotlinnlp.simplednn.core.layers.LayerType
import com.kotlinnlp.simplednn.core.layers.StackedLayersParameters
import com.kotlinnlp.simplednn.core.layers.models.merge.mergeconfig.ConcatMerge
import com.kotlinnlp.simplednn.deeplearning.birnn.BiRNN
import com.kotlinnlp.utils.Serializer
import org.antlr.v4.runtime.misc.OrderedHashSet
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable

/**
 * The [FramesExtractor] parameters.
 *
 * @property name the name of this model (it should be unique, used to distinguish it among more models)
 * @property intentsConfiguration the list of all the possible intents managed by this frame extractor
 * @param tokenEncodingSize the size of the tokens encodings
 * @param hiddenSize the size of the hidden layer of the BiRNNs
 * @param hiddenActivation the activation of the hidden layer of the BiRNNs
 * @param recurrentConnectionType the connection type of the recurrent layer of the BiRNNs
 */
class FramesExtractorModel(
  val name: String,
  val intentsConfiguration: List<Intent.Configuration>,
  internal val tokenEncodingSize: Int,
  hiddenSize: Int,
  hiddenActivation: ActivationFunction? = Tanh,
  recurrentConnectionType: LayerType.Connection = LayerType.Connection.LSTM
) : Serializable {

  companion object {

    /**
     * Private val used to serialize the class (needed by Serializable).
     */
    @Suppress("unused")
    private const val serialVersionUID: Long = 1L

    /**
     * Read a [FramesExtractorModel] (serialized) from an input stream and decode it.
     *
     * @param inputStream the [InputStream] from which to read the serialized [FramesExtractorModel]
     *
     * @return the [FramesExtractorModel] read from [inputStream] and decoded
     */
    fun load(inputStream: InputStream): FramesExtractorModel = Serializer.deserialize(inputStream)
  }

  /**
   * The offsets of the slots indices (within the flatten list of all the slots) for each intent configuration.
   */
  val slotsOffsets: List<Int> = this.intentsConfiguration.indices.map { intentIndex ->
    this.intentsConfiguration.take(intentIndex).sumBy { config -> config.slots.size }
  }

  /**
   * The set of indices of the "no-slot" classes within the slots classification.
   */
  val noSlotIndices: OrderedHashSet<Int> = run {

    val orderedSet = OrderedHashSet<Int>()

    this.intentsConfiguration.flatMap { it.slots }.withIndex()
      .filter { it.value == Intent.Configuration.NO_SLOT_NAME }
      .forEach { orderedSet.add(it.index) }

    orderedSet
  }

  /**
   * A BiRNN model.
   */
  val biRNN1 = BiRNN(
    inputType = LayerType.Input.Dense,
    inputSize = tokenEncodingSize,
    hiddenSize = hiddenSize,
    hiddenActivation = hiddenActivation,
    recurrentConnectionType = recurrentConnectionType,
    outputMergeConfiguration = ConcatMerge())

  /**
   * A BiRNN model.
   */
  val biRNN2 = BiRNN(
    inputType = LayerType.Input.Dense,
    inputSize = tokenEncodingSize,
    hiddenSize = hiddenSize,
    hiddenActivation = hiddenActivation,
    recurrentConnectionType = recurrentConnectionType,
    outputMergeConfiguration = ConcatMerge())

  /**
   * The output network for the intent prediction.
   */
  val intentNetwork = StackedLayersParameters(
    layersConfiguration = listOf(
      LayerInterface(
        size = 2 * this.biRNN1.hiddenSize + 2 * this.biRNN2.hiddenSize, // always the concatenation of the last outputs
        type = LayerType.Input.Dense),
      LayerInterface(
        size = this.intentsConfiguration.size,
        connectionType = LayerType.Connection.Feedforward,
        activationFunction = Softmax())
    )
  )

  /**
   * The output network for the slots prediction.
   */
  val slotsNetwork: StackedLayersParameters

  /**
   * The parameters of this model.
   */
  val params: FrameExtractorParameters

  init {

    // There is a 2 x factor because it includes Beginning + Inside for each slot class.
    val slotsNetworkOutputSize: Int = 2 * this.intentsConfiguration.sumBy { it.slots.size }

    this.slotsNetwork = StackedLayersParameters(
      layersConfiguration = listOf(
        LayerInterface(
          size = slotsNetworkOutputSize + this.biRNN1.outputSize + this.biRNN2.outputSize,
          type = LayerType.Input.Dense),
        LayerInterface(
          size = slotsNetworkOutputSize,
          connectionType = LayerType.Connection.Feedforward,
          activationFunction = Softmax())
      ))

    this.params = FrameExtractorParameters(
      biRNN1Params = this.biRNN1.model,
      biRNN2Params = this.biRNN2.model,
      intentNetworkParams = this.intentNetwork,
      slotsNetworkParams = this.slotsNetwork
    )
  }

  /**
   * Serialize this [FramesExtractorModel] and write it to an output stream.
   *
   * @param outputStream the [OutputStream] in which to write this serialized [FramesExtractorModel]
   */
  fun dump(outputStream: OutputStream) = Serializer.serialize(this, outputStream)

  /**
   * Get the offset index from which the slots of a given intent start, within the concatenation of all the possible
   * intents slots.
   *
   * @param intentName the name of an intent
   *
   * @return the offset of the given intent slots
   */
  fun getSlotsOffset(intentName: String): Int =
    this.slotsOffsets[this.intentsConfiguration.indexOfFirst { it.name == intentName }]

  /**
   * @param intentIndex the index of an intent
   *
   * @return the range of slots indices of the given intent, within the concatenation of all the possible intents slots
   */
  fun getSlotsRange(intentIndex: Int): IntRange {

    val slotsOffset: Int = this.slotsOffsets[intentIndex]

    return slotsOffset until slotsOffset + this.intentsConfiguration[intentIndex].slots.size
  }
}
