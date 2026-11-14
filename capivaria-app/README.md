# Capivaria Shop BR – App Android

App cliente da loja https://capivariashopbr.store/ (não guarda dados da loja: tudo vem da API `https://capivariashopbr.store/api/v1`).

## O que o app faz
- **Loja**: catálogo atualizado ao abrir/retomar o app e a cada 30 s com a loja visível. Produtos esgotados continuam na lista, com a compra desativada.
- **Comprar**: cria o pedido (`POST /orders`), mostra o QR Code e o "copia e cola" do Pix a partir do `payload` recebido. O app nunca marca como pago: o botão "Já paguei" só avisa a loja.
- **Pedidos**: lista só os pedidos da própria conta, com status, sorteio (o resultado vem da loja, o app só anima), chat do pedido (depois do Pix confirmado) e avaliação após a entrega.
- **Conta**: login pelo navegador do sistema (OAuth 2.0 Authorization Code + PKCE S256). O app não vê a senha, não tem segredo no APK e guarda os tokens cifrados no Android Keystore. Contas bloqueadas/removidas mostram o motivo e não podem comprar nem enviar mensagens.
- Gestão avançada, cupons, suporte e painel de administrador abrem no site.

## Como gerar o APK
1. Suba todos os arquivos para um repositório no GitHub (branch `main`). A pasta oculta `.github` precisa ir junto.
2. Aba **Actions** → **Build APK** (roda a cada push, ou *Run workflow*).
3. O workflow baixa a logo da loja sozinho. Se preferir, coloque o arquivo em `app/src/main/res/drawable-nodpi/logo_capivaria.png` e ele não baixa.
4. Quando terminar, baixe **CapivariaShop-apk** em *Artifacts*, descompacte e instale o `app-debug.apk`.

O APK de teste é assinado com a chave de depuração. Para publicar, assine uma versão release em uma ferramenta Android externa, com a sua chave. Nunca coloque a chave privada no repositório.

## Ajustes
- Callback do login: `br.store.capivariashop:/oauth2redirect` (em `Config.java` e no `AndroidManifest.xml`). É o valor que o app registra como `redirect_uri` no provedor.
- Nomes de campos do JSON (título, preço, status etc.) são lidos de forma tolerante em `J.java` e `MainActivity.java`. Se algum campo da API tiver outro nome, é só acrescentar a chave.
