provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "TMT"
      ManagedBy = "terraform"
      Env       = var.env
    }
  }
}
