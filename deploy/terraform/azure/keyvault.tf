# Key Vault holds the DB credentials. The CD pipeline reads these at deploy time
# to build the Kubernetes Secret - secrets never live in git or GitHub.
resource "azurerm_key_vault" "this" {
  name                       = "${var.prefix}-kv-${random_string.suffix.result}"
  resource_group_name        = azurerm_resource_group.this.name
  location                   = var.location
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
  name         = "pg-user"
  value        = var.db_admin_user
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.kv_admin]
}

resource "azurerm_key_vault_secret" "db_password" {
  name         = "pg-password"
  value        = random_password.db.result
  key_vault_id = azurerm_key_vault.this.id
  depends_on   = [azurerm_role_assignment.kv_admin]
}

# Full JDBC URL the backend consumes (org.postgresql.Driver against the
# primary database on the HA Flexible Server). sslmode=require encrypts the
# connection; Flexible Server presents a certificate chained to a well-known
# CA so the JVM's default truststore validates it without extra setup.
resource "azurerm_key_vault_secret" "db_url" {
  name         = "pg-url"
  value        = "jdbc:postgresql://${azurerm_postgresql_flexible_server.this.fqdn}:5432/${var.db_name}?sslmode=require"
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
