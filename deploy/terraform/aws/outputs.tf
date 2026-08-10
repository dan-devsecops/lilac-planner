output "region" {
  value = var.region
}

output "ecr_registry" {
  description = "Registry host for image.registry / docker push (<acct>.dkr.ecr.<region>.amazonaws.com)."
  value       = split("/", aws_ecr_repository.this["lilac-planner-backend"].repository_url)[0]
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "db_secret_name" {
  description = "Secrets Manager secret holding MARIADB_USER/PASSWORD/URL."
  value       = aws_secretsmanager_secret.db.name
}

output "rds_address" {
  value = aws_db_instance.this.address
}

output "jdbc_url" {
  value = "jdbc:mariadb://${aws_db_instance.this.address}:3306/${var.db_name}"
}

# -------- GitHub Actions config --------
output "github_actions_role_arn" {
  description = "-> GitHub secret AWS_ROLE_ARN (assume-role via OIDC)."
  value       = var.enable_github_oidc ? aws_iam_role.github[0].arn : null
}
