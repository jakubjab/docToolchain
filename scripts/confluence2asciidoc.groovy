/**
@Grapes(
        [@Grab('org.jsoup:jsoup:1.8.2'),
         @Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.6' ),
         @Grab('org.apache.httpcomponents:httpmime:4.5.1')]
)
**/
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.jsoup.nodes.Entities.EscapeMode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Document.OutputSettings
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.EncoderRegistry
import groovyx.net.http.ContentType
import java.security.MessageDigest
//to upload attachments:
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.HttpMultipartMode
import groovyx.net.http.Method
import groovy.xml.*

def baseUrl

// configuration
def config
try {
    println "scriptBasePath: ${scriptBasePath}"
    config = new ConfigSlurper().parse(new File(scriptBasePath, 'ConfluenceExportConfig.groovy').text)
} catch(groovy.lang.MissingPropertyException e) {
    //no scriptBasePath, works for some szenarios
    config = new ConfigSlurper().parse(new File('scripts/ConfluenceExportConfig.groovy').text)
}

def confluenceSpaceKey

// for getting better error message from the REST-API
void trythis (Closure action) {
    try {
        action.run()
    } catch (HttpResponseException error) {
        println "something went wrong - got an http response code "+error.response.status+":"
        println error.response.data
        throw error
    }
}

def findChildren

findChildren = { pageId ->
    def api = new RESTClient(config.confluenceAPI)
    def headers = [
            'Authorization': 'Basic ' + config.confluenceCredentials,
            'Content-Type':'application/json; charset=utf-8'
    ]
    //this fixes the encoding
    api.encoderRegistry = new EncoderRegistry( charset: 'utf-8' )

    trythis {
        pages = api.get(path: "content/${pageId}/child/page",
                        headers: headers).data.results
    }
    def result = []
    if (pages) {
        println "Child pages of ${pageId}:"
        pages.each { page ->
            println "   ${page.id} (${page.title})"
            result << ['id' : page.id, 'title' : page.title ]
            result += findChildren page.id
        }
    }
    return result
}

def pullFromConfluenceById = { pageId ->
    def api = new RESTClient(config.confluenceAPI)
    def headers = [
            'Authorization': 'Basic ' + config.confluenceCredentials,
            'Content-Type':'application/json; charset=utf-8'
    ]

    //this fixes the encoding
    api.encoderRegistry = new EncoderRegistry( charset: 'utf-8' )

    trythis {
        page = api.get(path: 'content/' + pageId,
                       query: ['expand' : 'body.storage,version'],
                       headers: headers).data
    }
    if (page) {
        def body = page.body.storage.value.toString().trim()
        
        println "Fetched page ${pageId} (${page.title})"
        
        return body;
    } else {
        println "Page ${pageId} not found"
    }
}

def getIdbyTitle = { pageTitle ->
    def api = new RESTClient(config.confluenceAPI)
    def headers = [
            'Authorization': 'Basic ' + config.confluenceCredentials,
            'Content-Type':'application/json; charset=utf-8'
    ]
    //this fixes the encoding
    api.encoderRegistry = new EncoderRegistry( charset: 'utf-8' )

    trythis {
        page = api.get(path: 'content',
                       query: ['spaceKey': confluenceSpaceKey,
                               'title'   : pageTitle],
                       headers: headers).data.results[0]
    }
    if (page) {
        return page.id;
    } else {
        println "page not found: "+pageTitle
    }
}

def saveToFile = { name, body ->
    def file = new File("confluence/${name}.xml")
    file << body
}

def runCommand = { strList ->
  def proc = strList.execute()
  proc.in.eachLine { line -> println line }
  proc.out.close()
  proc.waitFor()

  print "[INFO] ( "
  if(strList instanceof List) {
    strList.each { print "${it} " }
  } else {
    print strList
  }
  println " )"

  if (proc.exitValue()) {
    println "gave the following error: "
    println "[ERROR] ${proc.getErrorStream()}"
  }
  assert !proc.exitValue()
}

def tidyFile = { name ->
    def cmd = "tidy -m -i -wrap 100 -quiet -xml confluence/${name}.xml".toString()
    runCommand cmd
}

def convertToAsciidoc = { name ->
    def cmd = "pandoc -f html -t asciidoc -o confluence/${name}.adoc confluence/${name}.xml".toString()
    runCommand cmd
}

config.input.each { inputTitle ->
    println "Fetching ${inputTitle}"
    def inputId = getIdbyTitle inputTitle
    println "    ID: ${inputId}"

    def allIds = [['id' : inputId, 'title' : inputTitle]]
    allIds += findChildren inputId
    println "Exporting "+allIds.size()+" pages: ${allIds}"

    allIds.each { page ->
        def filename = page.title.replaceAll("[ (),.]", "_")
        def body = pullFromConfluenceById page.id
        saveToFile filename, body
        tidyFile filename
        convertToAsciidoc filename
    }
}
""