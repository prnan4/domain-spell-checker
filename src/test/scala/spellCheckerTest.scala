import com.typesafe.config.ConfigFactory
import org.scalatest.FunSuite
class spellCheckerTest extends FunSuite{

  test("Testing the scraping tool"){
    val categoryMap = scraping.extractCategories()
    val pdfsLocation = ConfigFactory.load().getString("spellChecker.testLocation")
    val listOfLinks = scraping.extractPaperLinks(categoryMap, 2, pdfsLocation)
    assert(scraping.savePdfs(listOfLinks, pdfsLocation))
  }

  test("Building dummy word corpus by parsing 2 pdfs"){
    val pdfsLocation = ConfigFactory.load().getString("spellChecker.testLocation")
    val writeLocation = ConfigFactory.load().getString("spellChecker.testCorpus")
    assert(parsePdf.parse(2,writeLocation, pdfsLocation))
  }

  test("Input is an empty string"){
    assert(spellChecker.testEdgeCases(" ")==Set.empty)
  }

  test("Input is a URL"){
    assert(spellChecker.testEdgeCases("http://www.wikipedia.org")==Set.empty)
  }

  test("Input contains additional leading/trailing spaces "){
    assert(spellChecker.testEdgeCases("   infection ") == scala.collection.mutable.Set[String]("infection"))
  }

  test("Input has extra white spaces"){
    assert(spellChecker.testEdgeCases("rhe umat ic")== scala.collection.mutable.Set[String]("rheumatic"))
  }

  test("Input is in upper case"){
    assert(spellChecker.testEdgeCases("MALIGNAT")== scala.collection.mutable.Set[String]("malignan", "valignat", "malignant"))
  }

  test("Input contains a combination of upper and lower case"){
    assert(spellChecker.testEdgeCases("DiAbEts")== scala.collection.mutable.Set[String]("diabet", "diabetes"))
  }

  test("Input contains single digit in combination with alphabets"){
    assert(spellChecker.testEdgeCases("pulm0nology") == scala.collection.mutable.Set[String]("pulmonology"))
  }

  test("Input contains single special character in combination with alphabets"){
    assert(spellChecker.testEdgeCases("microbiolog-") == scala.collection.mutable.Set[String]("microbiology", "microbiolog"))
  }

  test("Input contains more than one special character or number"){
    assert(spellChecker.testEdgeCases("biorxiv+/98") ==Set.empty)
  }

  test("Input is a number"){
    assert(spellChecker.testEdgeCases("9887688239") ==Set.empty)
  }

  test("Input is a character"){
    assert(spellChecker.testEdgeCases("-")==Set.empty)
  }

}
