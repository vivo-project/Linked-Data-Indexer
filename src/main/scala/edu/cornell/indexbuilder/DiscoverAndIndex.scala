package edu.cornell.indexbuilder

import akka.actor.Actor._
import akka.actor.Actor
import akka.routing.Routing._
import com.weiglewilczek.slf4s.Logging
import edu.cornell.indexbuilder.VitroVersion._
import edu.cornell.indexbuilder.http.Http
import edu.cornell.indexbuilder.indexing._
import edu.cornell.indexbuilder.discovery._

/*
 * This represents a basic process to run a site index.
 * Individuals discovered on the site specified by siteUrl
 * will be indexed to the service indicated by solrUrl.
 *
 * siteUrl - URL of site to indes ex "http://vivo.exampe.edu" No / at end
 * solrUrl - URL of solr sevice ex "http://solr.example.edu:8080/solr"
 * classUris - List of URIs of classes to index from siteUrl
 * siteVersion - Should be either "1.2" or "1.3" to indicate
 * which version of the vivo software is running.
 */
class DiscoverAndIndex(
  siteUrl:String,
  siteName:String,
  solrUrl:String,
  classUris:List[String],
  siteVersion:VitroVersion
) extends Object with Logging {

  def run (){

    MasterWorker.setupJenaStatic

    val maxConnections = 10 
    val http = new Http( maxConnections )

    //TODO: come up with a better place for working dirs
    val workDirectory = "./workDir" + System.currentTimeMillis()
    logger.debug("working directory is " + workDirectory)

    val actionName =
      if(r1dot2 == siteVersion ){
        logger.debug("server expected to be 1.2 or earlier VIVO version")
        VivoUriDiscoveryWorker.rel12actionName
      }else{
        logger.debug("server expected to be 1.3 or later VIVO version")
        VivoUriDiscoveryWorker.rel13actionName 
      }

    //setup connection to solr server
    val solrServer = SolrIndexWorker.makeSolrServer( solrUrl )

    //setup URI discovery
    val uriDiscoveryWorker = 
      Actor.actorOf(new VivoUriDiscoveryWorker(classUris, actionName, workDirectory))
    logger.debug("discoery worker setup with classUris: " + classUris)

    val selectorGen = new SelectorGeneratorForVivo(siteUrl);

    //Setup and start a master server to coordinate the work
    val master = Actor.actorOf( 
      new MasterWorker( 
        siteUrl, 
        siteName,
        uriDiscoveryWorker, 
        solrServer, 
        selectorGen,
        http
      ))

    master.start()
    master ! DiscoverUrisForSite( siteUrl )
    logger.debug("master created, started, and discovery for site requested")
  }


}
