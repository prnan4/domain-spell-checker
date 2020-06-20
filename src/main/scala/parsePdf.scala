/**
 * Processes the pdfs from file location and generates word corpus
 */
import scala.io.Source
import java.io.PrintWriter

import com.typesafe.config.ConfigFactory
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.logging.log4j.scala.Logging

object parsePdf extends Logging {
  val parsingBatchSize = ConfigFactory.load().getString("spellChecker.parsingBatchSize").toInt
  var Stopwords = Map[String, List[String]]()
  var count = 0
  var batchCount = 0
  System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")

  /**
   * Removes regular expressions from the input
   * @param txt Input to be processed
   * @param regEx Regular expresion to be removed
   * @return Text that does not contain the given regular expression
   */
  def removeRegex(txt: String, regEx: String, typeRegEx:String): String = {
    logger.trace(s"parsePdf removeRegex()::entry")
    val tStart = System.currentTimeMillis()
    var cleaned = txt
    Some(regEx) match {
      case Some(value) =>
        if (value.equals("white_space")) cleaned = txt.replaceAll(value, "")
        else cleaned = txt.replaceAll(value, " ")
    }
    val tEnd = System.currentTimeMillis()
    logger.debug(s"parsePdf:removeRegex: Removing $typeRegEx from text executed in ${tEnd - tStart}ms ")
    logger.trace("parsePdf:removeRegex::normal_exit")
    cleaned
  }

  /**
   * Removes custom words from the input
   * @param txt Input to be processed
   * @param flag Key to the stop words that are to be removed
   * @return Text free of stop words
   */
  def removeCustomWords(txt: String, flag: String): String = {
    logger.trace(s"parsePdf removeCustomWords()::entry")
    val tStart = System.currentTimeMillis()
    var words = txt.split(" ")
    Stopwords.get(flag) match {
      case Some(value) => words = words.filter(x => !value.contains(x))
      case None => println("No stopword flag matched")
    }
    val tEnd = System.currentTimeMillis()
    logger.debug(s"parsePdf:removeCustomWords: Removing custom words from text executed in ${tEnd - tStart}ms ")
    logger.trace("parsePdf:removeCustomWords::normal_exit")
    words.mkString(" ")
  }

  /**
   * Calls the function to remove regular expressions and stop words
   * @param text Input to be processed
   * @return Cleaned text
   */
  def preprocess(text:String): String = {
    logger.trace(s"parsePdf preprocess()::entry")
    val tStart = System.currentTimeMillis()
    var returnText = removeRegex(text,ConfigFactory.load().getString("spellChecker.regEx.urls"), "url")
    returnText = removeRegex(returnText,ConfigFactory.load().getString("spellChecker.regEx.punctuation"), "punctuations")
    returnText = removeRegex(returnText,ConfigFactory.load().getString("spellChecker.regEx.smallWords"), "small words")
    returnText = removeRegex(returnText,ConfigFactory.load().getString("spellChecker.regEx.largeWords"), "large words")
    returnText = removeRegex(returnText,ConfigFactory.load().getString("spellChecker.regEx.whiteSpace"), "white spaces")
    returnText = removeRegex(returnText,ConfigFactory.load().getString("spellChecker.regEx.geneSequences"), "gene sequences")
    returnText = removeCustomWords(returnText, "english")
    val tEnd = System.currentTimeMillis()
    logger.debug(s"parsePdf:preprocess: Preprocessing the text executed in ${tEnd - tStart}ms ")
    logger.trace("parsePdf:preprocess::normal_exit")
    returnText
  }

  /**
   * Converts the text to map(unique word -> frequency) and stores it in the file location
   * @param text to be converted to map
   * @param writeLocation Location to where corpus has to be saved
   */
  def writeCorpusToFile(text:String, writeLocation:String, batchStep:String) = {
    logger.trace(s"parsePdf writeCorpusToFile()::entry")
    val tStart = System.currentTimeMillis()
    val map = text.split(" ").toSeq.groupBy(identity).mapValues(_.size) //newly created word map
    var corpusMap = spellChecker.convertTextCorpusToMap(writeLocation) //existing word map

    //Merging the two word maps
    map.foreach(keyValue => {
      if (corpusMap.contains(keyValue._1)) {
        val frequency = keyValue._2 + corpusMap.get(keyValue._1).get
        corpusMap += keyValue._1 -> frequency
      }
      else corpusMap += keyValue._1 -> keyValue._2
    })

    //Removing words with frequency 1 and 2
    if (batchStep == "final"){
      logger.debug(s"parsePdf:writeCorpusToFile: Removing words with frequency 1, 2")
      corpusMap collect{
        case (word, 1) => corpusMap = corpusMap.-(word)
        case (word, 2) => corpusMap = corpusMap.-(word)
      }
    }

    var newText = ""
    corpusMap.foreach(keyValue =>{ newText = newText + s"${keyValue._1},${keyValue._2}\n" })
    new PrintWriter(writeLocation) { write(newText); close }
    val tEnd = System.currentTimeMillis()
    logger.debug(s"parsePdf:writeCorpusToFile: Writing corpus to file executed in ${tEnd - tStart}ms ")
    logger.info(s"parsePdf:writeCorpusToFile: Number of distinct words in corpus: ${corpusMap.size} ")
    logger.trace("parsePdf:writeCorpusToFile::normal_exit")
  }

  /**
   * To parse the pdfs and return the status of parsing
   * @return whether building and saving the corpus was succefull
   */
  def parse(noPdfsParse:Int, writeLocation:String, pdfsLocation:String): Boolean ={
    logger.trace(s"parsePdf parse()::entry")
    logger.info(s"parsePdf:parse Name of the pdf being parsed: ")
    val tStart = System.currentTimeMillis()
    var preprocessedText = ""
    new PrintWriter(writeLocation) { write(preprocessedText); close }
    Stopwords += ("english" -> Source.fromFile(ConfigFactory.load().getString("spellChecker.stopWords")).getLines().toList)

    new java.io.File(pdfsLocation).listFiles.filter(_.getName.endsWith(".pdf")).withFilter(file => count != noPdfsParse).map(file=> {
      if (file.length() != 0) {
        logger.info(s"parsePdf:parse $file  ${count+1}")
        val pdf = PDDocument.load(file)
        var text = (new PDFTextStripper).getText(pdf).toLowerCase

        //To remove references and author names
        if (text.indexOf(" https://doi") != -1) {
          if (text.indexOf(" references ") == -1) text = text.substring(text.indexOf(" https://"))
          else if(text.indexOf(" references ") < text.lastIndexOf(" references "))text = text.substring(text.indexOf(" https://doi"), text.lastIndexOf(" references "))
        }
        preprocessedText = preprocessedText + preprocess(text)
        pdf.close()
        count = count +1
      }

      //Updating the corpus in batch mode [1 batch = 1000 papers]
      if(count % parsingBatchSize == 0){
        logger.debug(s"parsePdf:parse Building corpus for batch ${batchCount+1}")
        writeCorpusToFile(preprocessedText, writeLocation, "intermediate")
        preprocessedText = ""
        batchCount = batchCount + 1
        true
      }
    })

    writeCorpusToFile(preprocessedText, writeLocation, "final")

    val tEnd = System.currentTimeMillis()
    logger.info(s"parsePdf:parse: Building the full corpus executed in ${tEnd - tStart}ms ")
    logger.trace("parsePdf:parse::normal_exit")
    true
  }

  /**
   * Checks if the given string is a number or not
   * @param noPdfsParse Number of pdfs to parse
   * @return true if string is a number
   */
  def isAllDigits(noPdfsParse: String) = noPdfsParse forall Character.isDigit

  def main(args: Array[String]) = {
    println("--------------------PDF PARSING TOOL--------------------\n\nThis tool allows you to parse pdfs from a given file location and build word corpus from it.\n")
    print("Enter the file location where the pdfs are saved: ")
    val pdfsLocation = scala.io.StdIn.readLine();
    print("Enter the number of pdfs to parse [Enter -1 to parse all pdfs from the file location]: ")
    val noPdfsParse  =scala.io.StdIn.readLine()

    if(isAllDigits(noPdfsParse)) {
      print("Enter the location where you wish to save the corpus: ")
      val writeLocation = scala.io.StdIn.readLine()
      println()
      if(parse(noPdfsParse.toInt, writeLocation, pdfsLocation)) println(s"Built word corpus succesfully")
    }
    else print("Enter a valid number")
  }

}
