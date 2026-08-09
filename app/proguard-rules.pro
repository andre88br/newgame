# O estado dos jogos e o registro das partidas são serializados por kotlinx.serialization,
# que gera um objeto companion Serializer para cada classe anotada. O R8 não vê essas
# referências e removeria os serializadores, quebrando o "fechar o app e voltar onde
# parou" só na versão de release — o pior tipo de defeito para descobrir.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
