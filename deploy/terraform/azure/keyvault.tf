# Key Vault holds the DB credentials. The CD pipeline reads these at deploy time
# to build the Kubernetes Secret - secrets never live in git or GitHub.
resource "azurerm_key_vault" "this" {
  name                       = "${var.prefix}-kv-${random_string.suffix.result}"
  resource_group_name        = azurerm_resource_group.this.name
  location                   = azurerm_resource_group.this.location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  soft_delete_retention_days = 7
  tags                       = var.tags
}

# Let the identity running Terraform write secrets.
resource "azurerm_role_assignment" "kv_admin" {
  scope                = azurerm_key_vault.this.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_key_vault_secret" "db_user" {
  name         = "mariadb-user"
  value        = var.db_admin_user
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.kv_admin]
}

resource "azurerm_key_vault_secret" "db_password" {
  name         = "mariadb-password"
  value        = random_password.db.result
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.kv_admin]
}

# Full JDBC URL the backend consumes (MariaDB driver against Azure MySQL).
# sslMode=verify-full encrypts AND validates the server certificate + hostname
# (sslMode=trust would skip validation, leaving the connection open to MITM).
# Relies on Azure's CA (DigiCert Global Root G2) being in the JVM truststore,
# which it is on modern JREs. If a handshake fails on first deploy, fall back
# to verify-ca, or mount the Azure CA PEM and point serverSslCert at it.
resource "azurerm_key_vault_secret" "db_url" {
  name         = "mariadb-url"
  value        = "jdbc:mariadb://${azurerm_mysql_flexible_server.this.fqdn}:3306/${var.db_name}?sslMode=verify-full"
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.kv_admin]
}

# --- Native auth secrets (consumed only when AUTH_PROVIDER=native; harmless otherwise) ---
# The HS256 signing secret for native JWTs. Generated here so it never lives in git.
resource "random_password" "jwt" {
  length  = 48
  special = false # alphanumeric → >= 32 bytes and shell-safe in the CD pipeline
}

resource "azurerm_key_vault_secret" "native_jwt_secret" {
  name         = "native-jwt-secret"
  value        = random_password.jwt.result
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.kv_admin]
}

# Bootstrap admin password. Retrieve it once to log in:
#   az keyvault secret show --vault-name <kv> -n native-admin-password --query value -o tsv
# then change it in-app. The username/email are non-secret (set as GitHub Variables).
resource "random_password" "native_admin" {
  length  = 20
  special = false
}

resource "azurerm_key_vault_secret" "native_admin_password" {
  name         = "native-admin-password"
  value        = random_password.native_admin.result
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.kv_admin]
}
