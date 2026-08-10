# Strong, generated admin password - never stored in code or tfvars.
resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#$%*-_=+"
}

# Azure Database for MySQL Flexible Server (private access via the delegated subnet).
# The MariaDB JDBC driver used by the backend connects to MySQL over TLS.
resource "azurerm_mysql_flexible_server" "this" {
  name                   = "${var.prefix}-mysql-${random_string.suffix.result}"
  resource_group_name    = azurerm_resource_group.this.name
  location               = azurerm_resource_group.this.location
  administrator_login    = var.db_admin_user
  administrator_password = random_password.db.result
  version                = var.mysql_version
  sku_name               = var.mysql_sku

  delegated_subnet_id = azurerm_subnet.mysql.id
  private_dns_zone_id = azurerm_private_dns_zone.mysql.id

  storage {
    size_gb = var.mysql_storage_gb
  }

  backup_retention_days = 7
  tags                  = var.tags

  depends_on = [azurerm_private_dns_zone_virtual_network_link.mysql]
}

resource "azurerm_mysql_flexible_database" "app" {
  name                = var.db_name
  resource_group_name = azurerm_resource_group.this.name
  server_name         = azurerm_mysql_flexible_server.this.name
  charset             = "utf8mb4"
  collation           = "utf8mb4_unicode_ci"
}

# Require TLS for all connections.
resource "azurerm_mysql_flexible_server_configuration" "require_secure" {
  name                = "require_secure_transport"
  resource_group_name = azurerm_resource_group.this.name
  server_name         = azurerm_mysql_flexible_server.this.name
  value               = "ON"
}
