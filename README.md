[README.md](https://github.com/user-attachments/files/35417478/README.md)
# Path Voice

[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) の近接ボイスを、**直線距離ではなく「音が通れる最短ルート」の距離**で減衰させるクライアント専用 MOD です。

壁の向こうにいる相手の声は、ドアや通路を回り込んだ距離のぶん小さく聞こえます。閉じた部屋の中にいる相手の声は聞こえません。

## 特徴

- 自分が**サバイバル / アドベンチャー**のときだけ有効です。クリエイティブ / スペクテイターでは通常の直線距離に戻ります。
- **クライアント専用**です。サーバーに導入する必要はありません（サーバー側には Simple Voice Chat が必要です）。
- 閉じたドア・トラップドア・フェンスゲートは音を遮断し、開ければ通します。
- ルートで聞こえる範囲を超える相手、ルートが見つからない相手の声は無音になります。

## 動作環境

| 項目 | バージョン |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Simple Voice Chat | 1.20.1-2.5.x（Forge 版） |
| Java | 17 |

## 導入方法

1. Forge 1.20.1 と Simple Voice Chat を導入します。
2. この MOD の jar を `mods` フォルダに入れます。
3. 接続先のサーバーにも Simple Voice Chat が入っていることを確認してください。

## 仕組み

1. クライアントの tick ごと（10 tick 間隔）に、自分の目の位置から周囲のプレイヤーの目の位置までを、3D グリッド上の A* で探索します。
2. 求めたルート距離をキャッシュしておきます。
3. Simple Voice Chat が受信した音声の「聞こえる範囲（半径）」を、Mixin で次の値に差し替えます。

   ```
   R' = R × 直線距離 / ルート距離
   ```

   SVC は直線距離で音量を計算しますが、半径をこのように縮めることで、結果としてルート距離で減衰したのと同じ音量になります。

### 音が通れるブロック

| ブロック | 判定 |
| --- | --- |
| 空気 | 通す |
| 当たり判定がフルブロックでないもの（柵、ガラス板、ハーフブロックの隙間、水など） | 通す |
| ドア / トラップドア / フェンスゲート | 開いていれば通す、閉じていれば遮断 |
| フルブロック | 遮断 |
| 未ロードのチャンク・ワールドの高さ範囲外 | 遮断 |

斜め方向への移動では、壁の角をすり抜けないように隣接ブロックも確認しています。

## 制限事項

- **音の方向（左右の定位）は直線のまま**です。変わるのは音量の減衰だけです。
- サーバー設定の範囲より遠い相手の音声は、そもそもサーバーから届かないため聞こえません。
- 範囲に入った直後の短い間（最大 10 tick 程度）は、ルートを計算するまで直線距離で再生されます。
- 減衰カーブは線形だと仮定して計算しています。
- グループ通話には影響しません。
- Simple Voice Chat の内部クラス（`PlayerSoundPacket`）に Mixin しているため、SVC のバージョンが変わると動かなくなる可能性があります。

## ビルド

```bash
./gradlew build
```

成果物は `build/libs/` に出力されます。

## ライセンス

[0BSD](https://opensource.org/license/0bsd)

```
Copyright (C) 2026

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

Simple Voice Chat は henkelmax 氏による別の MOD で、それ自体のライセンスに従います。
