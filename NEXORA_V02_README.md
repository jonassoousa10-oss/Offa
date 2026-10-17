# Nexora VPN v0.2 (teste)
Projeto Android VPN independente, compilado no GitHub Actions.

## O que a v0.2 faz
- Selecionar operadora (Automática, Vivo, Claro, TIM, Oi, Outra)
- Selecionar servidor publicado por um serviço HTTPS autorizado
- Conectar com um toque, sem pedir para o usuário importar arquivos de configuração
- Criar a chave WireGuard do cliente no próprio telefone
- Solicitar configuração individual no momento da conexão
- Guardar configuração local com criptografia do Android Keystore
- Exibir estados de conexão e tráfego do motor WireGuard existente

## IMPORTANTE: servidores reais não incluídos
O app NÃO fornece gratuitamente internet móvel e NÃO inclui servidores VPN públicos ou segredos embutidos.
Sem um serviço Nexora ativo, a tela informa honestamente "Serviço ainda não configurado" e não finge estar conectada.

Para um uso sem configuração por usuário, o distribuidor precisa compilar com a URL da API oficial e prover infraestrutura WireGuard, cadastro de chaves e autenticação/rate limiting. Na versão de teste, há "⚙ Serviço" para informar manualmente a URL HTTPS de um servidor próprio; isso serve apenas para administração e diagnóstico.

## API HTTPS necessária
GET /v1/servers

Retorno:
{
  "servers": [
    {
      "id": "br-01",
      "name": "Brasil 01",
      "region": "São Paulo",
      "operators": ["auto", "vivo", "claro", "tim", "oi"]
    }
  ]
}

POST /v1/sessions
Content-Type: application/json

Requisição:
{
  "server_id": "br-01",
  "operator": "vivo",
  "public_key": "<WireGuard client public key, Base64>"
}

Retorno (exemplo de esquema, **não** valores utilizáveis):
{
  "address": "10.99.0.2/32",
  "peer_public_key": "<server real WireGuard public key>",
  "endpoint": "vpn.example.org:51820",
  "dns": ["1.1.1.1"],
  "allowed_ips": "0.0.0.0/0, ::/0",
  "persistent_keepalive": 25
}

Obrigação do backend: registrar a public_key recebida como peer no servidor autorizado, reservar um endereço IP exclusivo por cliente, manter atribuições e revogações, impor limites e devolver um peer realmente alcançável. O APK não pode configurar esse servidor remotamente por conta própria e não deve receber a chave privada do servidor.

Sugestão de proteção em produção: autenticar dispositivos/usuários antes de emitir sessões, adicionar limites por IP/conta/dispositivo, expiração e renovação, restringir acesso aos endpoints, manter política de privacidade e logs mínimos. Não embutir credenciais ou chaves privadas compartilhadas no APK.

## Observações
- Nesta etapa, o motor segue **WireGuard**. SSH direto/SSH + Proxy/SSH + SSL do Rev Hunter são protocolos diferentes e não foram implementados ou anunciados como funcionais.
- WireGuard UP significa túnel local ativo; só testes de handshake/tráfego comprovam a conectividade real.
- A VPN não cria dados móveis sem plano ativo nem contorna automaticamente cobranças da operadora.
- Branch de desenvolvimento temporária: nexora-v02-automatic-servers-20261017.
