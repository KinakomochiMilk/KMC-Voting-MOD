# KMCVoting (Kinakomochi Minecraft Voting System)
Minecraft Forge 1.12.2 向けの投票システムMODです。

Votifier v2互換の投票受付、投票時のコマンド実行、Votifier v1向けRSA公開鍵提供などを行います。

KMCVotingはMODの内部データとして使用し、正式名称としてKinakomochi Minecraft Voting Systemとしています。

---

## 公開鍵・秘密鍵・トークンについて

KMC Votingでは、投票サービスとの接続に以下の3つの情報を使用します。

* **公開鍵（Public Key）**
* **秘密鍵（Private Key）**
* **トークン（Token）**

それぞれ用途が異なります。

### 公開鍵

公開鍵は、**Votifier v1方式の投票サービスで使用するための鍵**です。

KMC Votingを初回起動すると、自動的にRSA鍵ペアが生成されます。

```text
config/kmcvoting/
├── public.key
├── private.key
└── config.json
```

`public.key` は外部の投票サービスへ登録するために使用します。

公開鍵は、その名前の通り**第三者へ公開して問題ありません**。

Japan Minecraft Serversなど、Votifier v1方式に対応した投票サイトを利用する場合は、この公開鍵を登録してください。

### 秘密鍵

`private.key` は、公開鍵と対になる**秘密の鍵**です。

```text
config/kmcvoting/private.key
```

秘密鍵はKMC Voting内部で使用されるため、**第三者へ公開したり、投票サイトへ登録したりしないでください。**

また、GitHubなどの公開リポジトリへアップロードしないよう注意してください。

秘密鍵が外部へ流出すると、Votifier v1方式の認証に悪用される可能性があります。

### トークン

トークンは、**Votifier v2方式の投票認証に使用する秘密情報**です。

KMC Votingの設定ファイルに保存されています。

```text
config/kmcvoting/config.json
```

例：

```json
{
  "host": "0.0.0.0",
  "port": 8192,
  "token": "ここにトークンが入ります"
}
```

トークンは投票サービス(MinePortal等)側とKMC Votingの間で投票を認証するために使用されます。

**トークンも秘密情報なので、第三者へ公開しないでください。**

GitHubへ設定ファイルを公開する場合は、実際に使用しているトークンを必ず削除・置換してください。

### まとめ

| 情報            | 用途              | 公開     |
| ------------- | --------------- | ------ |
| `public.key`  | Votifier v1の認証  | ✅ 公開可能 |
| `private.key` | Votifier v1の秘密鍵 | ❌ 非公開  |
| `token`       | Votifier v2の認証  | ❌ 非公開  |

**公開してよいのは `public.key` のみです。**

`private.key` と `token` は、サーバーの認証情報として安全に管理してください。

---

## config.json の設定

KMC Votingの設定は、以下のファイルで管理されます。

```text
config/kmcvoting/config.json
```

初回起動時にファイルが自動生成されます。

基本的な構成は以下のようになっています。

```json
{
  "host": "0.0.0.0",
  "port": 8192,
  "token": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "commands": [
    "say {player} voted on {service}!"
  ]
}
```

### `host`

Votifierの接続を待ち受けるアドレスを指定します。

```json
"host": "0.0.0.0"
```

`0.0.0.0` の場合、サーバーが持つすべてのネットワークインターフェースからの接続を受け付けます。

通常は、特別な理由がない限り変更する必要はありません。

### `port`

Votifierの待ち受けポートを指定します。

```json
"port": 8192
```

外部の投票サービスからKMC Votingへ接続する場合、このポートへ接続できるようにネットワーク側の設定も必要になります。

既存のVotifier設定とポートが重複しないように注意してください。

### `token`

Votifier v2方式で使用する認証トークンです。

```json
"token": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

初回起動時に自動生成されます。

このトークンは**秘密情報**です。投票サービス側へ設定する場合を除き、他人へ公開しないでください。

GitHubなどへ設定ファイルを公開する場合は、実際に使用しているトークンを削除・置換してください。

### `commands`

投票を受信したときにMinecraft上で実行するコマンドを指定します。

```json
"commands": [
  "economy forcode add {player}"
]
```

複数のコマンドを設定することもできます。

```json
"commands": [
  "economy forcode add {player}",
  "say {player} さんが投票しました！"
]
```

投票を受信すると、上から順番にすべてのコマンドが実行されます。

コマンドの先頭に `/` を付けても動作しますが、基本的には付けずに設定することを推奨します。

### 使用できるプレースホルダー

コマンドには、投票情報を自動的に埋め込むプレースホルダーを使用できます。

| プレースホルダー      | 内容                  |
| ------------- | ------------------- |
| `{player}`    | 投票したMinecraftプレイヤー名 |
| `{service}`   | 投票サービス名             |
| `{address}`   | 投票元アドレス             |
| `{timestamp}` | 投票時刻                |

#### `{player}`

投票したプレイヤーのMinecraftユーザー名に置き換えられます。

```text
give {player} diamond
```

例えば `kinakomoti_kun` が投票した場合、

```text
give kinakomoti_kun diamond
```

として実行されます。

投票報酬をプレイヤーへ付与する場合などに使用できます。

#### `{service}`

投票に使用されたサービス名に置き換えられます。

```text
say {player} voted on {service}!
```

例えばMinePortalからの投票であれば、投票サービス名が自動的に入ります。

#### `{address}`

投票データに含まれる投票元アドレスに置き換えられます。

```text
say Vote received from {address}
```

#### `{timestamp}`

投票時刻をUnix timestampとして置き換えます。

```text
say Vote time: {timestamp}
```

### 設定例

例えば、投票時に「ダイヤモンド」を付与し、同時にサーバー内へ通知する場合は次のように設定できます。

```json
{
  "host": "0.0.0.0",
  "port": 8192,
  "token": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "commands": [
    "give {player} diamond",
    "say {player} さんが投票しました！"
  ]
}
```

> **注意:** `config.json` を変更した場合は、設定を反映するためにMinecraftサーバーを再起動してください。

---

# 開発者向けAPI

KMC Votingでは、他のMinecraft MODから投票情報を受け取るためのAPIを提供しています。

APIは以下のクラスから利用できます。

```java
import jp.kinakomochi.kmcvoting.api.KMCVotingAPI;
```

---

## `KMCVotingAPI`

### `registerVoteListener()`

投票を受信したときに呼び出されるリスナーを登録します。

```java
KMCVotingAPI.registerVoteListener(vote -> {
    System.out.println(
        "投票者: " + vote.getUsername()
    );
});
```

`VoteListener` は以下のインターフェースです。

```java
public interface VoteListener {
    void onVote(Vote vote);
}
```

投票を受信すると、登録されたすべてのリスナーに対して `onVote()` が呼び出されます。

---

### `unregisterVoteListener()`

登録した投票リスナーを解除します。

```java
KMCVotingAPI.unregisterVoteListener(listener);
```

例えば、登録時にリスナーを変数へ保存しておきます。

```java
KMCVotingAPI.VoteListener listener = vote -> {
    System.out.println(
        "投票者: " + vote.getUsername()
    );
};

KMCVotingAPI.registerVoteListener(listener);
```

不要になった場合は、

```java
KMCVotingAPI.unregisterVoteListener(listener);
```

で解除できます。

---

## `Vote`

投票情報を保持するクラスです。

```java
KMCVotingAPI.Vote
```

### `getUsername()`

投票したMinecraftプレイヤー名を取得します。

```java
String username = vote.getUsername();
```

---

### `getAddress()`

投票データに含まれるアドレスを取得します。

```java
String address = vote.getAddress();
```

---

### `getTimestamp()`

投票時刻をUnix timestampとして取得します。

```java
long timestamp = vote.getTimestamp();
```

---

### `getServiceName()`

投票に使用されたサービス名を取得します。

```java
String service = vote.getServiceName();
```

---

## 投票イベントの例

以下のように、投票を受信した際に独自の処理を実行できます。

```java
KMCVotingAPI.registerVoteListener(vote -> {

    String player = vote.getUsername();
    String service = vote.getServiceName();

    System.out.println(
        player + " が " + service + " から投票しました！"
    );

});
```

これにより、他のMODからKMC Votingの投票イベントを利用できます。

例えば、

* 独自の投票報酬システム
* 投票回数の集計
* GUIへの投票情報表示
* 独自のログ保存
* 他のゲーム内システムとの連携

などに利用できます。

---

## `getPublicKeyPem()`

KMC Votingが使用しているRSA公開鍵をPEM形式の文字列として取得します。

```java
String publicKey = KMCVotingAPI.getPublicKeyPem();
```

取得できるのは**公開鍵のみ**です。

秘密鍵を取得するAPIは提供されていません。

Votifier v1方式との連携など、公開鍵を必要とする処理に利用できます。

---

## `createVote()`

`Vote`オブジェクトを生成します。

```java
KMCVotingAPI.Vote vote =
    KMCVotingAPI.createVote(
        "PlayerName",
        "127.0.0.1",
        System.currentTimeMillis(),
        "ExampleService"
    );
```

引数は次の順番です。

```text
createVote(
    username,
    address,
    timestamp,
    serviceName
)
```

| 引数            | 型        | 内容      |
| ------------- | -------- | ------- |
| `username`    | `String` | プレイヤー名  |
| `address`     | `String` | 投票元アドレス |
| `timestamp`   | `long`   | 投票時刻    |
| `serviceName` | `String` | 投票サービス名 |

---

## `fireVote()`

作成した `Vote` を登録済みのすべてのリスナーへ通知します。

```java
KMCVotingAPI.fireVote(vote);
```

通常のMODでは、投票を受信したときにKMC Voting自身がこの処理を行うため、**外部MODが通常の投票処理として直接呼び出す必要はありません。**

主にAPIを利用したテストや、独自の連携処理で使用できます。

---

## API一覧

| API                        | 説明              |
| -------------------------- | --------------- |
| `registerVoteListener()`   | 投票リスナーを登録       |
| `unregisterVoteListener()` | 投票リスナーを解除       |
| `getPublicKeyPem()`        | RSA公開鍵を取得       |
| `createVote()`             | `Vote`オブジェクトを生成 |
| `fireVote()`               | 投票イベントをリスナーへ通知  |
| `Vote#getUsername()`       | プレイヤー名を取得       |
| `Vote#getAddress()`        | アドレスを取得         |
| `Vote#getTimestamp()`      | 投票時刻を取得         |
| `Vote#getServiceName()`    | 投票サービス名を取得      |

> **注意:** APIの仕様はKMC Votingのバージョンによって変更される場合があります。MOD開発時は、使用しているKMC Votingのバージョンに対応したAPI仕様を確認してください。

---

# 注意事項

KMC Votingを利用する際は、以下の点に注意してください。

## 秘密情報の取り扱い

* `config/kmcvoting/config.json` に含まれる `token` は、外部に公開しないでください。
* `config/kmcvoting/private.key` は、絶対に公開・共有しないでください。
* `public.key` は公開して問題ありませんが、`private.key` とセットで管理しないよう注意してください。
* GitHubなどの公開リポジトリに、`token` や `private.key` を含む設定ファイルをアップロードしないでください。

## ポートについて

* KMC Votingの初期ポートは `8192` です。
* 他のMODやサービスが同じポートを使用している場合、KMC Votingを起動できないことがあります。
* `port` を変更した場合は、外部の投票サイトなどの接続先も合わせて変更してください。

## 投票について

* 投票サイト側の設定とKMC Voting側の設定が一致している必要があります。
* 特に、接続先・ポート・Tokenなどの設定を間違えると、投票を正常に受信できません。
* プレイヤー名は、投票時に送信された名前がそのまま使用されます。
* プレイヤー名を対象とするコマンドを設定している場合、名前の入力間違いなどに注意してください。

## コマンド設定について

`commands` に設定したコマンドは、投票を受信した際にMinecraft内で実行されます。

* `{player}`、`{service}`、`{address}`、`{timestamp}` のプレースホルダーを使用できます。
* コマンドの記述を間違えると、投票自体は受信できても報酬処理などが正常に行われない場合があります。
* 外部MODのコマンドを使用する場合は、そのMODが正常に導入・動作していることを確認してください。
* コマンドの内容によっては、サーバー運営やゲーム内データに影響を与える可能性があります。

## バックアップについて

KMC Votingの設定を変更する前に、必要に応じて設定ファイルをバックアップしてください。

特に以下のファイルは、再生成や変更によって接続設定などに影響する可能性があります。

```text
config/kmcvoting/config.json
config/kmcvoting/rsa/private.key
config/kmcvoting/rsa/public.key
```

## 不具合が発生した場合

投票を正常に受信できない場合は、以下を確認してください。

1. KMC Votingが正常に起動しているか
2. `config.json` の `host` / `port` / `token` が正しいか
3. 投票サイト側の接続設定が正しいか
4. ポートが他のサービスと競合していないか
5. `commands` の記述に間違いがないか
6. Minecraftのログにエラーが出ていないか

設定を変更した場合は、Minecraftを再起動してから再度確認してください。

---

> **重要:** `token` と `private.key` はKMC Votingのセキュリティに関わる重要な情報です。
> 公開リポジトリ、スクリーンショット、ログなどに含めないよう十分注意してください。

## コードについて
- 本プログラムは私が適当に書いたものを一度AIを通し、整理しております。その過程でコードがぎちぎちに詰まっていたり、スカスカになっている部分がございます。ご了承ください。
- 本プログラムのソースコードは公開予定がなかったため、見ずらい等の苦情は受け付けません。
- 本プログラムのライセンスは`License`を確認してください。
