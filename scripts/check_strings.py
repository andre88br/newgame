#!/usr/bin/env python3
"""Confere os textos do app contra o catálogo do motor.

O motor decide *o que* dizer (`ReasonKey`, `SpeechKey`) e o app decide *em que idioma*.
Nada liga as duas coisas em tempo de compilação: acrescentar uma chave e esquecer o texto
compila, instala e só aparece quando alguém tem o lance recusado — em português, no meio de
um app em inglês, ou pior, sem frase nenhuma.

Este script é essa ligação. Roda no CI e falha o build quando:

  * uma chave do motor não tem texto em `values/strings.xml`;
  * o texto em português divergiu do modelo de referência que está no motor;
  * `values-en/strings.xml` não traduz alguma coisa que existe em `values/`;
  * os marcadores (%1$s, %2$s) não batem entre os dois idiomas — que é o defeito que
    derruba o app na hora de formatar, e não na hora de traduzir.

Uso:
    python3 scripts/check_strings.py            confere e falha se algo estiver errado
    python3 scripts/check_strings.py --write-pt imprime o bloco pt-BR pronto para colar
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
MOTOR = RAIZ / "core-game/src/main/kotlin/io/github/andre88br/newgame/core"
FONTES = {
    "reason_": MOTOR / "engine/Reason.kt",
    "speech_": MOTOR / "a11y/BoardSpeech.kt",
}
PT = RAIZ / "app/src/main/res/values/strings.xml"
EN = RAIZ / "app/src/main/res/values-en/strings.xml"

# CHAVE("texto", arity = 2) — o texto pode estar na linha seguinte, em modelos longos.
ENTRADA = re.compile(
    r'^\s{4}([A-Z][A-Z0-9_]*)\(\s*\n?\s*"((?:[^"\\]|\\.)*)"',
    re.MULTILINE,
)
MARCADOR = re.compile(r"%(\d+)\$s")


def desescapar(texto: str) -> str:
    """O que o Kotlin escreve como `%1\\$s` é `%1$s` de verdade."""
    return texto.replace("\\$", "$").replace('\\"', '"')


def chaves_do_motor() -> dict[str, str]:
    """Nome do recurso -> modelo em português, lido direto do enum."""
    fora = {}
    for prefixo, arquivo in FONTES.items():
        fonte = arquivo.read_text(encoding="utf-8")
        # Só o corpo do enum interessa; o resto do arquivo tem outras strings.
        for nome, modelo in ENTRADA.findall(fonte):
            fora[prefixo + nome.lower()] = desescapar(modelo)
    return fora


def chaves_de_jogo() -> list[str]:
    """As `nameKey` declaradas no catálogo — uma por jogo que o app oferece."""
    catalogo = (MOTOR / "engine/GameCatalog.kt").read_text(encoding="utf-8")
    return re.findall(r'nameKey\s*=\s*"([^"]+)"', catalogo)


def textos(caminho: Path) -> dict[str, str]:
    if not caminho.exists():
        return {}
    raiz = ET.parse(caminho).getroot()
    return {
        item.get("name"): "".join(item.itertext())
        for item in raiz.findall("string")
    }


def marcadores(texto: str) -> set[str]:
    return set(MARCADOR.findall(texto))


def escapar_xml(texto: str) -> str:
    """Aspas simples e & precisam de escape em strings.xml."""
    return (
        texto.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
    )


def escrever_pt(catalogo: dict[str, str]) -> None:
    for nome, modelo in catalogo.items():
        print(f'    <string name="{nome}">{escapar_xml(modelo)}</string>')


def main() -> int:
    catalogo = chaves_do_motor()
    if not catalogo:
        print("erro: não achei chave nenhuma nos enums do motor", file=sys.stderr)
        return 2

    if "--write-pt" in sys.argv:
        escrever_pt(catalogo)
        return 0

    pt, en = textos(PT), textos(EN)
    problemas: list[str] = []

    for nome, modelo in sorted(catalogo.items()):
        if nome not in pt:
            problemas.append(f"{nome}: chave do motor sem texto em values/strings.xml")
        elif pt[nome] != modelo:
            problemas.append(
                f"{nome}: o português divergiu do motor\n"
                f"      motor: {modelo}\n"
                f"      app:   {pt[nome]}"
            )

    for nome, texto in sorted(pt.items()):
        if nome not in en:
            problemas.append(f"{nome}: sem tradução em values-en/strings.xml")
        elif marcadores(texto) != marcadores(en[nome]):
            problemas.append(
                f"{nome}: marcadores diferentes entre os idiomas\n"
                f"      pt: {sorted(marcadores(texto))} — {texto}\n"
                f"      en: {sorted(marcadores(en[nome]))} — {en[nome]}"
            )

    for nome in sorted(set(en) - set(pt)):
        problemas.append(f"{nome}: existe em inglês e não em português")

    # Todo jogo do catálogo precisa de regras escritas. Sem isto, um jogo novo entra e o
    # botão "Como jogar" abre um diálogo dizendo que ninguém escreveu as regras dele.
    for chave in chaves_de_jogo():
        regras = "rules_" + chave.removeprefix("game_")
        if regras not in pt:
            problemas.append(f"{regras}: {chave} está no catálogo e não tem regras escritas")

    if problemas:
        print(f"textos com problema ({len(problemas)}):\n", file=sys.stderr)
        for problema in problemas:
            print(f"  - {problema}", file=sys.stderr)
        return 1

    print(
        f"textos conferidos: {len(catalogo)} chaves do motor, "
        f"{len(pt)} textos em português, {len(en)} em inglês"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
