# Certificados extras para o build

Só é necessário em redes com inspeção de TLS (proxy corporativo ou antivírus que troca o certificado
dos sites). Sem isso, o build das imagens falha ao baixar dependências com erro de certificado.

Copie para esta pasta o certificado da autoridade usada pela inspeção, em formato PEM e com extensão
`.crt`. Todo `*.crt` daqui é aceito como confiável **apenas durante o build**; as imagens finais não o
recebem. Os arquivos `.crt` desta pasta não são versionados.
