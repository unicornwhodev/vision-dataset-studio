import com.unicornwhodev.visiondatasetstudio.core.workflow.*
import com.unicornwhodev.visiondatasetstudio.core.storage.*
import java.io.*
import java.nio.file.Files
import java.util.Properties
import kotlin.system.measureTimeMillis

private var passed=0
private fun test(name:String,block:()->Unit) { block();passed++;println("PASS $name") }
private fun rejects(block:()->Unit) { var caught=false;try{block()}catch(_:Exception){caught=true};check(caught) }
fun main() {
    val root=Files.createTempDirectory("vds-v4-qa-").toFile()
    try {
        test("strong validators accepted") { check(RangeSafety.strongEtag("\"abc\"")) }
        test("weak validators rejected") { check(!RangeSafety.strongEtag("W/\"abc\"")) }
        test("missing and malformed validators rejected") { listOf(null,"abc","\"a\nb\"").forEach{check(!RangeSafety.strongEtag(it))} }
        test("content range parsed exactly") { check(RangeSafety.parse("bytes 4-9/10")==RangeSafety.ContentRange(4,9,10)) }
        test("wildcard and overflow ranges rejected") { listOf("bytes 4-9/*","bytes 4-99/10","bytes 9-4/10","bytes 0-0/0","bytes 0-999999999999999999999/10").forEach{check(RangeSafety.parse(it)==null)} }
        test("correct resume accepted") { check(RangeSafety.validResume(4,"\"a\"","\"a\"",RangeSafety.parse("bytes 4-9/10"),10)) }
        test("etag changes rejected") { check(!RangeSafety.validResume(4,"\"a\"","\"b\"",RangeSafety.parse("bytes 4-9/10"),10)) }
        test("wrong offset rejected") { check(!RangeSafety.validResume(4,"\"a\"","\"a\"",RangeSafety.parse("bytes 3-9/10"),10)) }
        test("overbudget range rejected") { check(!RangeSafety.validResume(4,"\"a\"","\"a\"",RangeSafety.parse("bytes 4-10/11"),10)) }
        test("partial terminal ranges rejected") { check(!RangeSafety.validResume(4,"\"a\"","\"a\"",RangeSafety.parse("bytes 4-8/10"),10)) }
        val parent="a".repeat(40);val changed="b".repeat(40)
        test("same parent retries immutable intent") { check(PublicationSafety.decide(parent,parent,false)==ResumeDecision.RETRY_SAME_PARENT) }
        test("lost response reconciles bytes at changed head") { check(PublicationSafety.decide(parent,changed,true)==ResumeDecision.COMMITTED) }
        test("changed head without matching files is conflict") { check(PublicationSafety.decide(parent,changed,false)==ResumeDecision.CONFLICT) }
        test("invalid commit names rejected") { rejects{PublicationSafety.decide("main",changed,false)} }
        test("transfer and purge states lock edits") { check(listOf("PREPARED","PUBLISHING","CONFLICT","PUBLISHED","VERIFIED","PURGING","PURGED").all{it in PublicationSafety.lockedStates}) }
        val file=File(root,"stable.bin").apply{writeText("original")}
        test("successful replacement") { DurableFiles.replace(file){it.write("new".toByteArray())};check(file.readText()=="new") }
        test("failure preserves original bytes") { rejects{DurableFiles.replace(file){it.write("partial".toByteArray());throw IOException("injected ENOSPC")}};check(file.readText()=="new") }
        test("no pending file remains after handled failure") { check(root.listFiles()!!.none{it.name.endsWith(".pending")}) }
        test("bounded copy exact payload") { val out=ByteArrayOutputStream();check(DurableFiles.copyBounded("1234".byteInputStream(),out,4)==4L);check(out.toString()=="1234") }
        test("overbudget data rejected") { rejects{DurableFiles.copyBounded("12345".byteInputStream(),ByteArrayOutputStream(),4)} }
        test("cancellation propagates before copying") { val out=ByteArrayOutputStream();rejects{DurableFiles.copyBounded("abc".byteInputStream(),out,5,{error("cancel")})};check(out.size()==0) }
        test("write error propagates") { rejects{DurableFiles.copyBounded("abc".byteInputStream(),object:OutputStream(){override fun write(b:Int){throw IOException("device gone")}},5)} }
        test("read error propagates") { rejects{DurableFiles.copyBounded(object:InputStream(){override fun read():Int{throw IOException("provider removed")}},ByteArrayOutputStream(),5)} }
        test("hash known bytes") { check(DurableFiles.hash("abc".byteInputStream())=="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad") }
        test("hash rejects oversized provider") { rejects{DurableFiles.hash("abcd".byteInputStream(),3)} }
        test("owned cache paths accepted") { check(DurableFiles.ownedFile(root,file.path)==file.canonicalFile) }
        test("path traversal rejected") { rejects{DurableFiles.ownedFile(root,File(root,"../outside").path)} }
        test("root deletion rejected") { rejects{DurableFiles.ownedFile(root,root.path)} }
        test("symlink escape rejected") { val outside=Files.createTempFile("vds-outside","tmp");try{val link=File(root,"escape");Files.createSymbolicLink(link.toPath(),outside);rejects{DurableFiles.ownedFile(root,link.path)}}finally{Files.deleteIfExists(outside)} }
        val target=File(root,"package").apply{mkdir();File(this,"v").writeText("v1")}
        test("directory handover retains new generation") { val next=File(root,"building").apply{mkdir();File(this,"v").writeText("v2")};DurableFiles.replaceDirectory(next,target);check(File(target,"v").readText()=="v2");check(!File(target.path+".previous").exists()) }
        test("handover recovers previous generation after interrupted rename") { check(target.renameTo(File(target.path+".previous")));val next=File(root,"building").apply{mkdir();File(this,"v").writeText("v3")};DurableFiles.replaceDirectory(next,target);check(File(target,"v").readText()=="v3") }
        test("handover requires same parent") { rejects{DurableFiles.replaceDirectory(root,File(root,"nested/target"))} }
        val part=File(root,"network.part");val stateFile=File(root,"network.range")
        fun load():Properties=Properties().apply{stateFile.inputStream().use{load(it)}}
        fun seed(){part.writeText("first");DownloadCheckpoint.save(part,stateFile,"https://host/file","\"etag\"",20)}
        test("durable hashed prefix recovered") { seed();check(DownloadCheckpoint.recover(part,load(),"https://host/file",30)==5L) }
        test("unjournaled trailing bytes truncated") { seed();part.appendText("unfinished");check(DownloadCheckpoint.recover(part,load(),"https://host/file",30)==5L);check(part.readText()=="first") }
        test("corrupted prefix never resumed") { seed();part.writeText("wrong");check(DownloadCheckpoint.recover(part,load(),"https://host/file",30)==null) }
        test("changed source identity never resumed") { seed();check(DownloadCheckpoint.recover(part,load(),"https://host/other",30)==null) }
        test("reduced storage budget blocks resume") { seed();check(DownloadCheckpoint.recover(part,load(),"https://host/file",10)==null) }
        test("lost checkpoint blocks resume") { seed();check(DownloadCheckpoint.recover(part,Properties(),"https://host/file",30)==null) }
        test("shorter physical file blocks resume") { seed();part.writeText("fi");check(DownloadCheckpoint.recover(part,load(),"https://host/file",30)==null) }
        test("checkpoint rejects weak server validator") { rejects{DownloadCheckpoint.save(part,stateFile,"https://host/file","W/\"a\"",30)} }
        test("p50 and p95 use documented nearest rank") { check(PerformanceStats.percentile((1..20).map{it.toDouble()},0.5)==10.0);check(PerformanceStats.percentile((1..20).map{it.toDouble()},0.95)==19.0) }
        test("invalid benchmark samples rejected") { rejects{PerformanceStats.percentile(emptyList(),0.5)};rejects{PerformanceStats.percentile(listOf(Double.NaN),0.5)} }
        test("hash large streamed file without materializing payload") {
            val big=File(root,"stream.bin");big.outputStream().use{out->val chunk=ByteArray(65536){(it%251).toByte()};repeat(256){out.write(chunk)}}
            val h=big.inputStream().use{DurableFiles.hash(it,big.length())};check(h.length==64 && big.length()==16L*1024*1024)
        }
        println("PASS $passed V4 production-helper tests. Real JVM filesystem and hashing; no Android, HTTP client, Room or Compose runtime exercised.")
    } finally { root.deleteRecursively() }
}
