# YAKITORI

複数のブラウザプロファイルをワンタップで切り替えられる、Android 向け Chromium ベースブラウザ。

```
┌─────────────────────────────────────┐
│ 🟦 個人 │ 🟩 学校 │ 🟥 開発 │ 🕶 │ ＋ → │  ← 横スクロール式プロファイルバー
├─────────────────────────────────────┤  ← 選択中プロファイルの色ライン
│ ← │ → │ ⟳ │ アドレスバー             │
├─────────────────────────────────────┤
│            Webページ表示              │
├─────────────────────────────────────┤
│      [3] タブ     ＋      ⋮          │
└─────────────────────────────────────┘
```

## ビルド

| 項目 | 値 |
| --- | --- |
| minSdk | 26（Android 8.0） |
| target / compileSdk | 35 |
| 言語 | Kotlin（Android View + Material 3） |
| ビルド | Gradle 8.14.3 / AGP 8.7.3 / JDK 17 |

```sh
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions（`.github/workflows/android.yml`）が push のたびにデバッグ APK をビルドし、
`yakitori-debug-apk` アーティファクトとして公開します。

## 技術選定（仕様 18. 技術検証項目への回答）

### Chromium の採用方式：Android System WebView ＋ androidx.webkit Multi-Profile API

| 候補 | 評価 |
| --- | --- |
| Chromium をフォークして独自ビルド（Bromite/Cromite 方式） | ビルド環境 100GB 超・APK 100MB 超・セキュリティ更新を自前で追従する必要があり、MVP には過大 |
| GeckoView | Chromium ベースではないため不採用 |
| **Android System WebView（採用）** | 中身は Chromium。Play ストア経由で毎月自動更新され、APK は数 MB。WebView 123 以降は **Multi-Profile API** によりプロファイル単位でデータを分離可能 |

- **バージョン更新**: エンジン（Chromium）は端末の Android System WebView として Google Play から自動更新される。アプリ側の更新は不要。
- **APK サイズ**: エンジンを同梱しないため数 MB。
- **ライセンス**: WebView はシステムコンポーネントとして利用するだけで再配布しない。AndroidX / Material Components は Apache License 2.0。

### プロファイル分離

YAKITORI のプロファイル 1 つに WebView プロファイル（`yk_p_<id>`）を 1 つ割り当て、
タブの WebView 生成直後に `WebViewCompat.setProfile()` で紐付けます。
WebView プロファイルは Chromium の BrowserContext に相当するため、以下がエンジン側で分離されます。

| データ | 分離方法 |
| --- | --- |
| Cookie / ログイン状態 / セッション | WebView プロファイル（Profile.getCookieManager） |
| Local Storage / IndexedDB / Cache Storage / Service Worker | WebView プロファイル（Profile.getWebStorage 等） |
| HTTP キャッシュ | WebView プロファイル |
| 閲覧履歴 | アプリ DB でプロファイル ID ごとに管理 |
| タブ状態 | アプリ DB でプロファイル ID ごとに管理（最後に表示したタブも保持） |

- プロファイル切り替えでは WebView を破棄せずに保持するため、切り替え先のタブはそのままの状態で復元されます。
- **削除**: プロファイル削除時は、タブ・履歴の行を消したうえで Cookie・サイトデータを即時消去し、
  WebView プロファイルのディレクトリ削除を予約します。使用中で削除できない場合は、遅くとも次回起動時
  （WebView 生成前）に削除されます。プロファイル ID は再利用されないため、削除済みデータが新しいプロファイルに引き継がれることはありません。
- **非対応端末**（WebView 122 以前）: データ混在を防ぐため、先頭プロファイルのみ利用可能とし、プロファイル切り替えとシークレットモードを無効化したうえで WebView の更新を案内します。

### シークレットモード

- 起動ごとに使い捨ての WebView プロファイル（`yk_incognito_<時刻>`）を作成し、通常プロファイルと Cookie 等を完全に分離。
- 閲覧履歴・タブは保存しない。
- 「シークレットモードを終了」・最後のタブを閉じる・アプリ終了のいずれかで WebView を破棄し、プロファイルごと削除。
  プロセスが強制終了された場合も、次回起動時に残存データを削除。
- UI 全体を暗色にし、🕶 シークレットを強調表示。設定でスクリーンショット禁止（FLAG_SECURE）に対応（既定 ON）。

### Google Password Manager 連携（仕様 12）

独自のパスワード管理は持たず、Android Autofill フレームワークに委ねます。WebView は Autofill に対応しているため、
端末の自動入力サービスを「Google」にすると Google パスワード マネージャーの候補がログインフォームに表示されます。
設定 → パスワード・自動入力 から自動入力サービスの設定画面を開けます。
Autofill はページ内のフォームに対して動作するため、どのプロファイルでも同じ Google アカウントの保存パスワードが候補に出ます（サイトの Cookie 等は分離されたまま）。

### Google ログインへの配慮

Google などは「埋め込み WebView」からのログインを拒否する場合があるため、User-Agent から `; wv` 表記を除去し、
`X-Requested-With` ヘッダーを送出しない設定にしています。

### デベロッパーツール（仕様 13）

`WebView.setWebContentsDebuggingEnabled(false)` を明示し、DevTools / コンソール等の UI は搭載していません。

## MVP 実装範囲（仕様 17）

- **ブラウザ**: Web 表示、URL 入力・検索（URL 判定して検索にフォールバック）、戻る・進む・再読み込み／中止、ホーム、共有、ダウンロード、ファイルアップロード、動画全画面、外部アプリリンク（intent:// 等）
- **タブ**: 作成・切り替え・終了・一覧（ボトムシート）・すべて閉じる、プロファイルごとのタブ保持（再起動後も復元）
- **プロファイル**: 作成・編集（名前・アイコン）・削除（確認ダイアログ）、横スクロール式プロファイルバー、ワンタップ切り替え、データ分離
  - プロファイルバーのチップを長押しで「編集／閲覧データを削除／削除」
- **プライバシー**: シークレットモード、プロファイル単位の履歴（一覧・再表示・個別削除・一括削除）、閲覧データ削除（履歴／Cookie・サイトデータ／キャッシュを個別選択、期間指定）
- **設定**: ホームページ、検索エンジン（Google / Bing / DuckDuckGo / Yahoo! JAPAN）、新規タブ、ダウンロード確認、履歴、閲覧データ削除、シークレット設定、自動入力、プロファイル管理、バージョン情報

## 既知の制約

- Cookie・サイトデータ・キャッシュの削除は WebView の API 制約により期間指定できません（常に全期間）。履歴は期間指定に対応。
- サイトからのカメラ・マイク・位置情報の要求は MVP では常に拒否します。
- ユーザー操作を伴わないポップアップはブロックします。

## ソース構成

```
app/src/main/java/com/yakitori/browser/
├── YakitoriApp.kt          起動時の残存データ削除、DevTools 無効化
├── data/                   アプリ DB（プロファイル・タブ・履歴）と設定
├── engine/
│   ├── ProfileEngine.kt    WebView Multi-Profile とのブリッジ（分離・削除・シークレット）
│   ├── ProfileOps.kt       プロファイル削除
│   └── UrlUtil.kt          URL / 検索の判定
├── browser/BrowserTab.kt   タブ・空間（プロファイル／シークレット）のモデル
└── ui/                     MainActivity（ブラウザ画面）、プロファイルバー、各ダイアログ・画面
```
