#!/usr/bin/env python3
"""Gera os três efeitos sonoros do app, em `app/src/main/res/raw`.

Os sons são **sintetizados aqui**, e não baixados de um banco de efeitos. Dois motivos, e
nenhum deles é preciosismo:

* Licença. Efeito de banco vem com termos, atribuição e um arquivo que ninguém no projeto
  sabe de onde veio. Um seno somado a um ruído que decai é do projeto, e ponto.
* Revisão. Um `.wav` no diff é um blob opaco. Este script é a fonte: dá para ler, mudar a
  frequência e gerar de novo — e o que está no repositório é sempre o que ele produz.

São curtos de propósito (menos de 0,3 s) e graves o bastante para não incomodar num
celular sem fone. Rodar:

    python3 scripts/make_sounds.py
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

TAXA = 22050
SAIDA = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def escrever(nome: str, amostras: list[float]) -> None:
    """Grava mono, 16 bits, com uma rampa curta nas pontas para não estalar."""
    rampa = int(TAXA * 0.004)
    total = len(amostras)
    quadros = bytearray()

    for indice, valor in enumerate(amostras):
        ganho = 1.0
        if indice < rampa:
            ganho = indice / rampa
        elif indice > total - rampa:
            ganho = max(0.0, (total - indice) / rampa)
        # Corta antes de estourar: som distorcido em celular soa como defeito.
        inteiro = int(max(-1.0, min(1.0, valor * ganho)) * 30000)
        quadros += struct.pack("<h", inteiro)

    caminho = SAIDA / nome
    with wave.open(str(caminho), "wb") as arquivo:
        arquivo.setnchannels(1)
        arquivo.setsampwidth(2)
        arquivo.setframerate(TAXA)
        arquivo.writeframes(bytes(quadros))
    print(f"{caminho.name}: {len(quadros)} bytes, {total / TAXA:.3f} s")


def tom(frequencia: float, duracao: float, decaimento: float, volume: float = 0.6) -> list[float]:
    """Um seno que decai — a forma mais simples de alguma coisa batendo na mesa."""
    return [
        volume * math.sin(2 * math.pi * frequencia * n / TAXA) * math.exp(-decaimento * n / TAXA)
        for n in range(int(TAXA * duracao))
    ]


def somar(*camadas: list[float]) -> list[float]:
    tamanho = max(len(camada) for camada in camadas)
    fora = [0.0] * tamanho
    for camada in camadas:
        for indice, valor in enumerate(camada):
            fora[indice] += valor
    return fora


def main() -> None:
    SAIDA.mkdir(parents=True, exist_ok=True)

    # Lance: um toque seco e agudo, como peça de madeira encostando no tabuleiro.
    escrever("move.wav", somar(
        tom(660, 0.10, decaimento=45, volume=0.45),
        tom(990, 0.06, decaimento=70, volume=0.20),
    ))

    # Captura: mais grave e um pouco mais longo — pesa mais do que um lance comum.
    escrever("capture.wav", somar(
        tom(300, 0.18, decaimento=22, volume=0.50),
        tom(150, 0.20, decaimento=18, volume=0.35),
    ))

    # Fim de partida: duas notas subindo, curtas, sem virar fanfarra.
    primeira = tom(523, 0.14, decaimento=16, volume=0.40)
    segunda = [0.0] * int(TAXA * 0.10) + tom(784, 0.22, decaimento=12, volume=0.40)
    escrever("finish.wav", somar(primeira, segunda))


if __name__ == "__main__":
    main()
