output "resource_group" {
  value = azurerm_resource_group.this.name
}

output "acr_login_server" {
  description = "Registry host for image.registry / docker push."
  value       = azurerm_container_registry.this.login_server
}

output "acr_name" {
  value = azurerm_container_registry.this.name
}

output "aks_cluster_name" {
  value = azurerm_kubernetes_cluster.this.name
}

output "key_vault_name" {
  value = azurerm_key_vault.this.name
}

output "mysql_fqdn" {
  value = azurerm_mysql_flexible_server.this.fqdn
}

output "jdbc_url" {
  description = "Value stored in Key Vault secret 'mariadb-url'."
  value       = "jdbc:mariadb://${azurerm_mysql_flexible_server.this.fqdn}:3306/${var.db_name}?sslMode=trust"
}

# -------- GitHub Actions secrets (set these in the repo) --------
output "github_actions_client_id" {
  description = "-> GitHub secret AZURE_CLIENT_ID"
  value       = var.enable_github_oidc ? azuread_application.github[0].client_id : null
}

output "azure_tenant_id" {
  description = "-> GitHub secret AZURE_TENANT_ID"
  value       = data.azurerm_client_config.current.tenant_id
}

output "azure_subscription_id" {
  description = "-> GitHub secret AZURE_SUBSCRIPTION_ID"
  value       = data.azurerm_client_config.current.subscription_id
}
