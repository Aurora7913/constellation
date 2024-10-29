
# don't list any dependencies for this folder
.PHONY: ci_list_dependencies
ci_list_dependencies:
	@true

.PHONY: clean
clean:
	rm -rf .cicache || echo "nothing in cache to clean"
