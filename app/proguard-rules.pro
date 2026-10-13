# Regras padrão bastam para o código do app: ele não usa reflexão.

# PdfBox-Android (ler fatura em PDF): o decodificador JPEG 2000 é opcional e não vem junto;
# sem esta linha o R8 para o build por causa da classe ausente. Faturas com essas imagens continuam
# com o texto legível (só a imagem não é decodificada).
-dontwarn com.gemalto.jp2.**
