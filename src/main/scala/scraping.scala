/**
 * Downloads the first 100 pdfs from www.biorxiv.org site.
 */
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.JavaConverters._
import java.net.{HttpURLConnection, URL}
import java.io.{BufferedOutputStream, FileOutputStream}

import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.scala.Logging

import scala.collection.mutable.ListBuffer

object scraping extends Logging {
  var count = 0
  var batchCount = 0
  var saveCount = 0
  var noPages = 0
  val webPage = ConfigFactory.load().getString("spellChecker.webPageURL")
  val scrapingBatchSize = ConfigFactory.load().getString("spellChecker.scrapingBatchSize").toInt
  var listOfCategories = ListBuffer[String]()
  var listOfLinks = ListBuffer[String]()

  /**
   * Finds the number of pages in each category
   * @param category Link to the individual categories
   * @return the number of pages
   */
  def findNoPages(category:String): Int ={
    logger.trace(s"scraping findNoPage()::entry")
    val tStart = System.currentTimeMillis()
    val docTopic = connectToALink(category)
    docTopic.select("li[class]").asScala.foreach(link => {
      if(link.attr("class").length > 9 && link.attr("class").substring(0,10) == "pager-last") noPages= link.text().toInt })
    val tEnd = System.currentTimeMillis()
    logger.debug(s"scraping:findNoPage: Number of pages in the ${category.substring(35)} category: $noPages ")
    logger.debug(s"scraping:findNoPage: Finding number of pages in the ${category.substring(35)} executed in ${tEnd - tStart}ms ")
    logger.trace("scraping:findNoPage::normal_exit")
    noPages
  }

  /**
   * Extract links to all the categories from the website
   * @return List containing links to the categories
   */
  def extractCategories():ListBuffer[String] = {
    logger.trace(s"scraping extractCategories()::entry")
    logger.info(s"scraping:extractCategories: Extracting links to the 27 categories ")
    val tStart = System.currentTimeMillis()

    val mainPage = connectToALink(webPage)
    logger.debug(s"scraping:extractCategories: Links to the categories: ")
    mainPage.select("a[href]").asScala.map(link => {
      //To iterate through the individual categories
      if (link.attr("href").length > 10 && link.attr("href").substring(0, 11) == "/collection") {
        listOfCategories += webPage + link.attr("href")
        logger.debug(s"scraping:extractCategories: ${webPage + link.attr("href") }"); true
      }
    })
    val tEnd = System.currentTimeMillis()
    logger.debug(s"scraping:extractCategories: Extracting links to categories executed in ${tEnd - tStart}ms ")
    logger.trace("scraping::extractCategories:normal_exit")
    listOfCategories
  }

  /**
   * Extracts the links of pdfs to be downloaded
   * @param listOfCategories List containing links to the categories
   * @param noPdfsToDownload number of paper whose links to be extracted
   * @param pdfsLocation File location where pdfs must be saved
   * @return List containing links of the papers extracted
   */
  def extractPaperLinks(listOfCategories:ListBuffer[String], noPdfsToDownload:Int, pdfsLocation:String):ListBuffer[String] = {
    logger.trace(s"scraping extractPaperLinks()::entry")
    logger.debug(s"scraping:extractPaperLinks: Extracting paper links")
    val tStart = System.currentTimeMillis()
    listOfCategories.withFilter(category => count != noPdfsToDownload).foreach( category => {

      //To iterate through the individual pages in a given category
      (1 until findNoPages(category)).withFilter(i => count != noPdfsToDownload).foreach(i => {
        val docTopic = connectToALink(category + "?page=" + i.toString)
        logger.debug(s"scraping:extractPaperLinks: Papers in page $i of category ${category.substring(35)}:  ")
        //To iterate through individual links in a given page
        if(docTopic != null) {
          docTopic.select("a[href]").asScala.withFilter(paperLink => count != noPdfsToDownload).map(paperLink => {
            if (paperLink.attr("href").length > 15 && paperLink.attr("href").substring(0, 16) == "/content/10.1101"){
              listOfLinks += webPage + paperLink.attr("href") + ".full.pdf"
              count = count +1
              logger.debug(s"scraping:extractPaperLinks: ${webPage + paperLink.attr("href") + ".full.pdf"} $count")

              //Downloading pdfs in batch mode [1 batch = 8000 pdfs]
              if(count % scrapingBatchSize == 0) {
                logger.debug(s"scraping:extractPaperLinks: Downloading papers in batch ${batchCount+1}")
                savePdfs(listOfLinks, pdfsLocation)
                listOfLinks = ListBuffer[String]()
                batchCount = batchCount + 1
              }
            }
          })
        }
      })
    })
    val tEnd = System.currentTimeMillis()
    logger.debug(s"scraping:extractPaperLinks: Paper links extracted in ${tEnd - tStart}ms ")
    logger.trace("scraping::extractPaperLinks:normal_exit")
    listOfLinks
  }

  /**
   * Connects to a link and retrieves the document in the given link
   * @param link to connect to
   * @return document fetched from the link
   */
  def connectToALink(link:String):Document = {
    logger.trace(s"scraping connectToALink()::entry")
    val tStart = System.currentTimeMillis()
    var result:Document = null
    try result = Jsoup.connect(link).get()
    catch {
      case hse: org.jsoup.HttpStatusException =>  logger.warn("HTTP error fetching URL")
      case ste: java.net.SocketTimeoutException => logger.warn("socket timeout")
      case se: java.net.SocketException => logger.warn("operation timeout")
      case ioe: java.io.IOException => logger.warn("Empty input stream")
    }
    val tEnd = System.currentTimeMillis()
    logger.debug(s"scraping:connectToALink: Retrieved document from webpage $link in ${tEnd - tStart}ms ")
    logger.trace("scraping::connectToALink:normal_exit")
    result
  }

  /**
   * Saves the individual paper to the given file location
   * @param listOfLinks List containing the links of pdfs to be downloaded
   * @param pdfsLocation File location where pdfs must be saved
   */
  def savePdfs(listOfLinks:ListBuffer[String], pdfsLocation:String): Boolean={
    logger.trace(s"scraping savePdf()::entry")
    logger.info(s"scraping:savePdf Papers downloaded: ")
    val tStart = System.currentTimeMillis()

    listOfLinks.map(link => {
      logger.info(s"scraping:savePdf $link  ${saveCount+1}")
      try {
        val url = new URL(link)
        url.openConnection().asInstanceOf[HttpURLConnection].setRequestMethod("GET")
        val in = url.openConnection().asInstanceOf[HttpURLConnection].getInputStream
        new BufferedOutputStream(new FileOutputStream(pdfsLocation + link.substring(40).dropRight(9) + ".pdf")).write(Stream.continually(in.read).takeWhile(_ != -1).map(_.toByte).toArray)
        saveCount = saveCount+1
        in.close()
      }
      catch {
        case ste: java.net.SocketTimeoutException => logger.warn("socket timeout")
        case se: java.net.SocketException => logger.warn("operation timeout")
        case ome:  java.lang.OutOfMemoryError => logger.warn("java heap space")
        case ioe: java.io.IOException => logger.warn("Empty input stream")
      }
      true
    })
    val tEnd = System.currentTimeMillis()
    logger.info(s"scraping:connectToALink: Downloading pdfs executed in ${tEnd - tStart}ms ")
    logger.trace("scraping::savePdf:normal_exit")
    true
  }

  /**
   * Checks if the given string is a number or not
   * @param noPdfsToDownload Number of pdfs to download
   * @return true if string is a number
   */
  def isAllDigits(noPdfsToDownload: String) = noPdfsToDownload forall Character.isDigit

  def main(args: Array[String]) = {
    println("--------------------WEB SCRAPING TOOL--------------------\n\nThis tool allows you to extract pdfs from the https://www.biorxiv.org site and save them in desired file location.\n")
    print("Enter the number of pdfs to download [Enter -1 to download all pdfs from the site]: ")
    val noPdfsToDownload =scala.io.StdIn.readLine()

    if(isAllDigits(noPdfsToDownload)) {
      print("Enter the file location where you wish to save the pdfs: ")
      val pdfsLocation = scala.io.StdIn.readLine();
      println()
      listOfCategories = extractCategories()
      listOfLinks = extractPaperLinks(listOfCategories, noPdfsToDownload.toInt, pdfsLocation)
      if (savePdfs(listOfLinks, pdfsLocation)) logger.info(s"scraping:main Downloaded pdfs succesfully")
    }
    else print("Enter a valid number")
  }
}