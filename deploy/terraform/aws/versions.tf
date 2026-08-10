terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state - create the bucket/table once, then uncomment.
  # backend "s3" {
  #   bucket         = "lilac-tfstate"
  #   key            = "aws/lilac-planner.tfstate"
  #   region         = "eu-west-1"
  #   dynamodb_table = "lilac-tflock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = var.tags
  }
}
