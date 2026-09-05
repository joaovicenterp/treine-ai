package com.treineai.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/* ============================================================
   CAMADA DE NUVEM

   Todo o Firebase vive aqui, isolado do resto do app:
     • Authentication — contas por e-mail e senha. As senhas ficam
       com o Google, com segurança; nem o app nem nós as vemos.
     • Cloud Firestore — o progresso de cada usuário, guardado como
       um único bloco JSON (a mesma serialização do app). Assim não
       dependemos do mapeamento automático do Firestore e a estrutura
       nunca diverge da versão local.

   O Firebase se inicializa sozinho pelo google-services.json, então
   não há nada a configurar em tempo de execução.
   ============================================================ */
object Cloud {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid

    suspend fun signUp(name: String, email: String, password: String): FirebaseUser {
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = res.user ?: error("Falha ao criar a conta.")
        val display = name.trim().ifEmpty { email.trim().substringBefore('@') }
        try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(display).build()).await()
        } catch (_: Exception) {
            /* o nome de exibição é opcional: se falhar, seguimos com a conta criada */
        }
        return user
    }

    suspend fun signIn(email: String, password: String): FirebaseUser {
        val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return res.user ?: error("Não foi possível entrar.")
    }

    fun signOut() = auth.signOut()

    /** Envia um e-mail com o link de redefinição — o jeito seguro de recuperar senha. */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun deleteAccount() {
        val user = auth.currentUser ?: return
        val id = user.uid
        try { db.collection("users").document(id).delete().await() } catch (_: Exception) {}
        user.delete().await()
    }

    /** Sobe o progresso do usuário. O Firestore guarda offline e sincroniza sozinho. */
    suspend fun saveData(uid: String, json: String) {
        db.collection("users").document(uid)
            .set(mapOf("json" to json, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    /** Baixa o progresso do usuário; null quando a conta ainda não tem nada salvo. */
    suspend fun loadData(uid: String): String? {
        val snap = db.collection("users").document(uid).get().await()
        return snap.getString("json")
    }

    /** Traduz os erros do Firebase para mensagens claras em português. */
    fun translate(e: Throwable): String = when (e) {
        is FirebaseAuthUserCollisionException -> "Já existe uma conta com esse e-mail. Faça login."
        is FirebaseAuthWeakPasswordException -> "A senha precisa de pelo menos 6 caracteres."
        is FirebaseAuthInvalidUserException -> "Não encontramos uma conta com esse e-mail."
        is FirebaseAuthInvalidCredentialsException -> "E-mail ou senha incorretos."
        else -> {
            val m = e.message ?: ""
            when {
                m.contains("network", ignoreCase = true) -> "Sem conexão. Verifique sua internet e tente de novo."
                m.contains("badly formatted", ignoreCase = true) -> "Digite um e-mail válido."
                m.contains("blocked", ignoreCase = true) -> "Muitas tentativas. Aguarde um momento e tente de novo."
                else -> "Não foi possível concluir. Tente novamente."
            }
        }
    }
}
