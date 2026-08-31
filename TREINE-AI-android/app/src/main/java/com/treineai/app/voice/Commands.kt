package com.treineai.app.voice

import java.text.Normalizer

/* ============================================================
   COMANDOS DE VOZ
   Permitem conduzir o treino sem tocar no celular: a pessoa
   apoia o aparelho, se afasta e comanda falando.

   Nada aqui é obrigatório: sem microfone ou sem reconhecedor,
   o app segue funcionando inteiro por toque.
   ============================================================ */
object Commands {

    /** Minúsculas, sem acento e sem pontuação — igual ao `norm` da versão web. */
    fun norm(s: String?): String {
        val d = Normalizer.normalize(s ?: "", Normalizer.Form.NFD)
        return d.replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /* A ordem importa: frases mais específicas primeiro.
       "para de falar" precisa ganhar de "para". */
    val TABLE: List<Pair<String, List<String>>> = listOf(
        "mute" to listOf("para de falar", "pare de falar", "fica quieto", "silencio", "sem voz", "cala a boca"),
        "unmute" to listOf("pode falar", "voz ligada", "fala comigo", "volta a falar"),
        "next" to listOf("proximo", "proxima", "seguinte", "avancar", "avanca", "proximo exercicio"),
        "repeat" to listOf("repetir", "repete", "de novo", "outra vez", "mais uma serie"),
        "status" to listOf("quantas", "quanto falta", "como estou", "status", "qualidade", "quantas repeticoes"),
        "pause" to listOf("pausar", "pausa", "pausado", "espera", "espera ai"),
        "resume" to listOf("continuar", "continua", "retomar", "retoma", "seguir", "segue", "voltar a treinar"),
        "finish" to listOf("finalizar", "finaliza", "terminar", "termina", "encerrar", "encerra", "acabou", "acabei", "terminei"),
        "back" to listOf("voltar", "sair", "cancelar", "cancela"),
        "start" to listOf("comecar", "comeca", "comece", "iniciar", "inicia", "pode ir", "bora", "vamos la", "vai"),
        "stopall" to listOf("parar tudo", "parar", "para")
    )

    fun match(text: String?): String? {
        val t = " " + norm(text) + " "
        for ((cmd, phrases) in TABLE) {
            for (p in phrases) if (t.contains(" $p ")) return cmd
        }
        return null
    }
}
