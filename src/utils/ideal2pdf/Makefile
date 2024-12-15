all: unstable-transition.png stable-transition.png \
     positional-mode.png compact-mode.png \
     semantic-clustering.png \
     compact-and-semantic-clustering.png \
     first-class-cfg-sea.png first-class-cfg-cfg.png \
     first-class-cfg-highlight-sea.png first-class-cfg-highlight-cfg.png \
     regions.png \
     regions-collapsed-2.png \
     regions-collapsed-24.png \
     regions-collapsed-245.png \
     regions-collapsed-2456.png \
     regions-collapsed-24567.png \
     regions-collapsed-245679.png \
     regions-hierarchy.png \
     regions-hierarchy-collapsed.png

INPUT_GRAPH = test-input/1-factorial-small.xml

# Graph ids and names:
#
# ./ideal2pdf --list --filter "id in [47,48,52,55]" input/1-factorial-small.xml
#
# id  method          phase
# 47  Factorial::fac  Before beautify loops
# 48  Factorial::fac  PhaseIdealLoop iterations
# 52  Factorial::fac  Optimize finished
# 55  Factorial::fac  Global code motion

unstable-transition.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id in [47,48]" --igv-style --positional --animate --no-stable --no-show-folded --no-cluster-pinned --no-show-title
	mv sea.pdf $@

stable-transition.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id in [47,48]" --igv-style --positional --animate --stable --no-show-folded --no-cluster-pinned --no-show-title
	mv sea.pdf $@

compact-mode.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 52" --igv-style --no-extend-node-info --no-cluster-pinned --no-show-folded --dot --no-show-title
	mv 52-sea.pdf $@
	mv 52-sea.dot $@.dot

positional-mode.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 52" --igv-style --positional --no-extend-node-info --no-cluster-pinned --dot --no-show-title
	mv 52-sea.pdf $@
	mv 52-sea.dot $@.dot

semantic-clustering.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 52" --igv-style --positional --dot --no-show-title
	mv 52-sea.pdf $@
	mv 52-sea.dot $@.dot

compact-and-semantic-clustering.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 52" --igv-style --no-extend-node-info --no-show-folded --dot --no-show-title
	mv 52-sea.pdf $@
	mv 52-sea.dot $@.dot

first-class-cfg-sea.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --positional --no-cluster-pinned --no-remove-secondary --no-fold-projs --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

first-class-cfg-cfg.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --positional --no-cluster-pinned --no-remove-secondary --no-fold-projs --dot --no-show-title
	mv 55-cfg.pdf $@
	mv 55-cfg.dot $@.dot

first-class-cfg-highlight-sea.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --positional --no-cluster-pinned --no-remove-secondary --no-fold-projs --highlight "name == 'Phi'" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

first-class-cfg-highlight-cfg.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --positional --no-cluster-pinned --no-remove-secondary --no-fold-projs --highlight "name == 'Phi'" --dot --no-show-title
	mv 55-cfg.pdf $@
	mv 55-cfg.dot $@.dot

regions.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-collapsed-2.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --collapse "id in ['B2']" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-collapsed-24.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --collapse "id in ['B2', 'B4']" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-collapsed-245.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --collapse "id in ['B2', 'B4', 'B5']" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-collapsed-2456.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --collapse "id in ['B2', 'B4', 'B5', 'B6']" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-collapsed-24567.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --collapse "id in ['B2', 'B4', 'B5', 'B6', 'B7']" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-collapsed-245679.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --collapse "id in ['B2', 'B4', 'B5', 'B6', 'B7', 'B9']" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-hierarchy-pre.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-hierarchy.pdf: dot/regions-hierarchy.dot
	dot -Tpdf $< > $@

regions-hierarchy-collapsed-pre.pdf: $(INPUT_GRAPH)
	./ideal2pdf $< --filter "id == 55" --igv-style --hierarchical --positional --collapse "id in ['B2', 'B4', 'B5', 'B6', 'B7', 'B9']" --dot --no-show-title
	mv 55-sea.pdf $@
	mv 55-sea.dot $@.dot

regions-hierarchy-collapsed.pdf: dot/regions-hierarchy-collapsed.dot
	dot -Tpdf $< > $@

%.png: %.pdf
	pdftoppm $< $* -png

clean:
	rm -rf *.pdf *.png *.dot
