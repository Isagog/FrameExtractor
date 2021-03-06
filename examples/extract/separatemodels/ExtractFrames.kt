/* Copyright 2018-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package extract.separatemodels

import com.kotlinnlp.frameextractor.FramesExtractor
import com.kotlinnlp.frameextractor.TextFramesExtractorModel
import com.kotlinnlp.frameextractor.TextFramesExtractor
import com.kotlinnlp.linguisticdescription.sentence.Sentence
import com.kotlinnlp.linguisticdescription.sentence.token.FormToken
import com.kotlinnlp.neuraltokenizer.NeuralTokenizer
import com.kotlinnlp.neuraltokenizer.NeuralTokenizerModel
import com.kotlinnlp.simplednn.core.embeddings.EmbeddingsMap
import com.kotlinnlp.tokensencoder.embeddings.EmbeddingsEncoderModel
import com.kotlinnlp.tokensencoder.ensemble.EnsembleTokensEncoderModel
import com.kotlinnlp.tokensencoder.wrapper.TokensEncoderWrapperModel
import com.xenomachina.argparser.mainBody
import java.io.File
import java.io.FileInputStream

/**
 * Extract frames from a text using a Frame Extractor with a transient embeddings encoder, loading the embeddings
 * separately.
 *
 * Launch with the '-h' option for help about the command line arguments.
 */
fun main(args: Array<String>) = mainBody {

  val parsedArgs = CommandLineArguments(args)

  val tokenizer: NeuralTokenizer = parsedArgs.tokenizerModelPath.let {
    println("Loading tokenizer model from '$it'...")
    NeuralTokenizer(NeuralTokenizerModel.load(FileInputStream(File(it))))
  }

  val model: TextFramesExtractorModel = parsedArgs.modelPath.let {
    println("Loading text frames extractor model from '$it'...")
    TextFramesExtractorModel.load(FileInputStream(File(it)))
  }

  val embeddingsMap: EmbeddingsMap<String> = parsedArgs.embeddingsPath.let {
    println("Loading pre-trained word embeddings from '$it'...")
    EmbeddingsMap.load(it)
  }

  val firstEncoder: TokensEncoderWrapperModel<*, *, *, *> =
    (model.tokensEncoder as EnsembleTokensEncoderModel)
      .components.first().model as TokensEncoderWrapperModel<*, *, *, *>

  (firstEncoder.model as EmbeddingsEncoderModel.Transient).setEmbeddingsMap(embeddingsMap)

  val textFramesExtractor = TextFramesExtractor(model)

  @Suppress("UNCHECKED_CAST")
  while (true) {

    val inputText = readValue()

    if (inputText.isEmpty()) {

      break

    } else {

      tokenizer.tokenize(inputText).forEach { sentence ->

        sentence as Sentence<FormToken>

        val output: FramesExtractor.Output = textFramesExtractor.extractFrames(sentence)
        val frame = TextFramesExtractor.Frame(intent = output.buildIntent(), distribution = output.buildDistribution())

        println()
        frame.print(sentence)
      }
    }
  }

  println("\nExiting...")
}

/**
 * Read a value from the standard input.
 *
 * @return the string read
 */
private fun readValue(): String {

  print("\nExtract frames from a text (empty to exit): ")

  return readLine()!!
}

/**
 * Print this frame to the standard output.
 *
 * @param sentence the sentence from which this frame has been extracted
 */
private fun TextFramesExtractor.Frame.print(sentence: Sentence<FormToken>) {

  println("Intent: ${this.intent.name}")

  println("Slots: %s".format(
    if (this.intent.slots.isNotEmpty())
      this.intent.slots.joinToString(", ") { slot ->
        "(${slot.name} ${slot.tokens.joinToString(" ") { sentence.tokens[it.index].form }})"
      }
    else
      "None"))

  println("Distribution:")
  this.distribution.map.entries
    .sortedByDescending { it.value }
    .forEach { println("\t[%5.2f %%] %s".format(100.0 * it.value, it.key)) }
}
