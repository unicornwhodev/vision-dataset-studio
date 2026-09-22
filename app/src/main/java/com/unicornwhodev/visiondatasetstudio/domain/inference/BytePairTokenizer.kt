package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.io.ByteArrayOutputStream

/** Byte-level BPE for the published CLIP/Roberta tokenizer.json contracts. No remote code. */
class BytePairTokenizer(file:File) {
    private val root=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi.adapter(Any::class.java).fromJson(file.readText()) as Map<*,*>
    private val model=root["model"] as Map<*,*>
    private val vocab=(model["vocab"] as Map<*,*>).entries.associate{it.key.toString() to (it.value as Number).toInt()}
    private val inverse=vocab.entries.associate{it.value to it.key}
    private val ranks=(model["merges"] as List<*>).mapIndexed { i,item ->
        val pair=if(item is List<*>)item.map{it.toString()} else item.toString().split(' ',limit=2)
        require(pair.size==2);(pair[0] to pair[1]) to i
    }.toMap()
    private val suffix=model["end_of_word_suffix"]?.toString().orEmpty()
    private val clip=suffix=="</w>"
    private val processor=root["post_processor"] as Map<*,*>
    private val bos=((processor["cls"] as List<*>)[1] as Number).toInt()
    private val eos=((processor["sep"] as List<*>)[1] as Number).toInt()
    private val added=(root["added_tokens"] as List<*>).map{it as Map<*,*>}.associate{it["content"].toString() to (it["id"] as Number).toInt()}
    private val specialIds=added.values.toSet()
    private val bytesToChars:Map<Int,Char>
    private val charsToBytes:Map<Char,Int>
    private val clipPattern=Regex("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+")
    private val bytePattern=Regex("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")
    private val cache=mutableMapOf<String,List<Int>>()
    init {
        require(model["type"]=="BPE" && processor["type"]=="RobertaProcessing")
        require(model["dropout"]==null && model["byte_fallback"]==false)
        val values=((33..126)+(161..172)+(174..255)).toMutableList()
        val codes=values.toMutableList();var extra=0
        for(i in 0..255)if(i !in values){values.add(i);codes.add(256+extra++)}
        bytesToChars=values.zip(codes.map{it.toChar()}).toMap();charsToBytes=bytesToChars.entries.associate{it.value to it.key}
    }
    private fun word(text:String):List<Int> = cache.getOrPut(text) {
        val encoded=text.toByteArray(Charsets.UTF_8).map{bytesToChars.getValue(it.toInt() and 255).toString()}.toMutableList()
        if(encoded.isEmpty())emptyList() else {
            encoded[encoded.lastIndex]+=suffix
            while(encoded.size>1) {
                var index=-1;var rank=Int.MAX_VALUE
                for(i in 0 until encoded.lastIndex){val r=ranks[encoded[i] to encoded[i+1]] ?: continue;if(r<rank){rank=r;index=i}}
                if(index<0)break
                val pair=encoded[index] to encoded[index+1];var i=0
                while(i<encoded.lastIndex)if(encoded[i]==pair.first && encoded[i+1]==pair.second){encoded[i]+=encoded.removeAt(i+1);i++}else i++
            }
            encoded.map{vocab[it] ?: error(tr("Jeton BPE absent du vocabulaire", "BPE token missing from vocabulary"))}
        }
    }
    fun encode(text:String,capacity:Int,pad:Int):Pair<IntArray,IntArray> {
        require(text.length<=16000 && capacity in 1..512)
        if(cache.size>4096)cache.clear()
        val normalized=if(clip)Normalizer.normalize(text,Normalizer.Form.NFC).replace(Regex("\\s+")," ").trim().lowercase(Locale.ROOT) else text
        val ids=mutableListOf(bos)
        val special=Regex(added.keys.sortedByDescending{it.length}.joinToString("|"){Regex.escape(it)})
        var cursor=0
        fun append(part:String){(if(clip)clipPattern else bytePattern).findAll(part).forEach{ids.addAll(word(it.value))}}
        for(match in special.findAll(normalized)){append(normalized.substring(cursor,match.range.first));ids.add(added.getValue(match.value));cursor=match.range.last+1}
        append(normalized.substring(cursor));ids.add(eos)
        require(ids.size<=capacity){tr("Prompt trop long pour les $capacity jetons du modèle", "Prompt too long for the model's $capacity tokens")}
        return IntArray(capacity){ids.getOrElse(it){pad}} to IntArray(capacity){if(it<ids.size)1 else 0}
    }
    fun decode(ids:List<Int>,skipSpecial:Boolean=false):String {
        val result=StringBuilder();val bytes=ByteArrayOutputStream()
        fun flush(){result.append(bytes.toString("UTF-8"));bytes.reset()}
        for(id in ids){
            val token=inverse[id] ?: added.entries.firstOrNull{it.value==id}?.key ?: error(tr("ID jeton inconnu", "Unknown token ID"))
            if(id in specialIds){flush();if(!skipSpecial)result.append(token)}
            else for(ch in token.replace("</w>"," ")){val b=charsToBytes[ch];if(b==null){flush();result.append(ch)}else bytes.write(b)}
        };flush();return result.toString()
    }
}
