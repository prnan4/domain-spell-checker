/**
 * To chcek whether the given word is spelled correctly. If spelled incorrectly, suggests possible spellings.
 */
import com.typesafe.config.ConfigFactory
import parsePdf.removeRegex
import scala.collection.immutable.ListMap
import scala.io.Source
import org.apache.logging.log4j.scala.Logging


object spellChecker extends Logging{
  val alphabet: Seq[Char] = 'a' to 'z'
  val corpus = s"[${alphabet.head}-${alphabet.last}]+".r.findAllIn(Source.fromFile(ConfigFactory.load().getString("spellChecker.corpus")).mkString.toLowerCase).toSet
  val corpusText = ConfigFactory.load().getString("spellChecker.corpus")
  val editDistance = ConfigFactory.load().getString("spellChecker.editDistance").toInt
  val minEditDistance = ConfigFactory.load().getString("spellChecker.minEditDistance").toInt
  var input = ""

  /**
   * Converts given corpus text file into map (word -> frequncy)
   * @param filename corpus text file
   * @return map (word -> frequncy)
   */
  def convertTextCorpusToMap(filename: String)= {
    logger.trace(s"spellChecker convertTextCorpusToMap()::entry")
    val tStart = System.currentTimeMillis()
    var corpusMap = collection.mutable.Map.empty[String, Int]
    Source.fromFile(filename).getLines().foreach( line => {
      val split = line.split(",")
      val word = split(0)
      val frequency = split(1).toInt
      corpusMap += word -> frequency
    })
    val tEnd = System.currentTimeMillis()

    logger.debug(s"spellChecker:convertTextCorpusToMap: Converting text corpus to map executed in ${tEnd - tStart}ms ")
    logger.trace("spellChecker:convertTextCorpusToMap::normal_exit")
    corpusMap
  }
  val corpusMap = convertTextCorpusToMap(corpusText)

  /**
   * Performs character transformations on the given input
   * @param word User input
   * @return Set of strings that result from character transformations
   */
  def edit(word:String) = {
    logger.trace(s"spellChecker edit()::entry")
    val tStart = System.currentTimeMillis()
    val returnSet = Set.empty ++
      (for (i <- 0 until word.length) yield (word take i) + (word drop (i + 1))) ++ // Deletes
      (for (i <- 0 until word.length - 1) yield (word take i) + word(i + 1) + word(i) + (word drop (i + 2))) ++ // Transposes
      (for (i <- 0 until word.length; j <- alphabet) yield (word take i) + j + (word drop (i+1))) ++
      (for (i <- 0 until word.length; j <- alphabet) yield (word drop i) + j + (word take i)) ++ // Replaces
      (for (i <- 0 until word.length; j <- alphabet) yield (word take i) + j + (word drop i)) // Inserts

    val tEnd = System.currentTimeMillis()
    logger.debug(s"spellChecker:edit: Number of words on performing character transformations: ${returnSet.size} ")
    logger.debug(s"spellChecker:edit: Character transformations on input executed in ${tEnd - tStart}ms ")
    logger.trace("spellChecker:edit::normal_exit")
    returnSet
  }

  /**
   * Checks and corrects the spelling for the given input
   * @param input Input string
   */
  def spellCorrecter(input: String) = {
    logger.trace(s"spellChecker spellCorrecter()::entry")
    val tStart = System.currentTimeMillis()
    var resultSet =  scala.collection.mutable.Set[String]()
    if(corpus.contains(input)) { println("Correct spelling"); resultSet += input}
    else {
      println("Possible suggestions: ")
      if (corpus.contains(input.replaceAll("\\s", ""))) { println(input.replaceAll("\\s", "")); resultSet += input.replaceAll("\\s", "")}
      else {
        val returnText = removeRegex(input,ConfigFactory.load().getString("spellChecker.regEx.punctuation"), "punctuation")
        var resultMap = Map[String, Int]()

        var transformedWords = Set.empty[String]
        if(editDistance == minEditDistance) transformedWords = edit(returnText)
        else edit(returnText).foreach( i => transformedWords = transformedWords union edit(i))

        for (word <- transformedWords) if (corpus.contains(word)) {
          resultMap = resultMap + (word-> corpusMap.get(word).get)
        }
        for ((key,value) <- ListMap(resultMap.toSeq.sortWith(_._2 > _._2):_*)){ printf(s"$key\n"); resultSet += key}
        if (resultMap.size == 0) println("No matches found ")

      }
    }
    val tEnd = System.currentTimeMillis()
    logger.info(s"spellChecker:spellCorrector: Checked and corrected spelling for given input in ${tEnd - tStart}ms ")
    logger.trace("spellChecker:spellCorrecter::normal_exit")
    resultSet
  }

  /**
   * Test against edge cases
   * @param str Input string
   * @return whether the input can be processed by spell checker
   */
  def testEdgeCases(str:String) = {
    logger.trace(s"spellChecker testEdgeCases()::entry")
    val in = str.toLowerCase().trim()
    logger.trace("spellChecker:testEdgeCases::normal_exit")
    if (in.length==0) { println("Input cannot be empty") ; Set.empty}
    else if (in.matches(ConfigFactory.load().getString("spellChecker.regEx.urls"))) { println("Input should not be url"); Set.empty}
    else if (in.matches(ConfigFactory.load().getString("spellChecker.regEx.number"))) { println("Input cannot be a number"); Set.empty}
    else if (in.matches(ConfigFactory.load().getString("spellChecker.regEx.singleCharacter"))) { println("Input should have more than one character"); Set.empty}
    else if (in.matches(ConfigFactory.load().getString("spellChecker.regEx.singleCharacterDeviation"))) {spellCorrecter(in)}
    else if (in.matches(ConfigFactory.load().getString("spellChecker.regEx.valid"))) {spellCorrecter(in)}
    else {println("Invalid input") ; Set.empty}
  }

  def main(args: Array[String]) = {
    println("--------------------DOMAIN SPELL CHECKER-------------------\n\nThis tools checks whether a word is spelled correctly. If the spelling is incorrect, it suggests possible spellings.\nEnter -Q to Quit.\n")
    do{
      print("Enter word: ")
      input = scala.io.StdIn.readLine()
      if (input != "Q") testEdgeCases(input)
      println()
    }while(input != "Q")
    println("Thanks for using the spell checker tool!")
  }
}