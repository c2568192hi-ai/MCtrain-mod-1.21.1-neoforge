# trainmod (NeoForge 1.21.1 / サーバーサイド限定)

## jar の作り方 (どちらか)
A. GitHub Actions: このフォルダをGitHubにアップロード → Actions → build → Artifacts の trainmod-jar (レシピ等はビルド時に自動生成)
B. ローカル: JDK 21 と Gradle 8.10 を入れて、このフォルダで `gradle build` → build/libs/trainmod-1.0.5.jar

## 使い方
jar をサーバーの mods フォルダに入れるだけ。クライアントMOD不要。

## 数値・素材の変更
tools/gen.py を編集 → `python3 tools/gen.py` → 再ビルド
