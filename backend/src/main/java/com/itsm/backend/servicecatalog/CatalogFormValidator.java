package com.itsm.backend.servicecatalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itsm.backend.common.error.InvalidFieldsException.FieldProblem;
import com.itsm.backend.servicecatalog.CatalogField.CatalogFieldType;

/** Checks a catalog item's form definition, and a request's answers against it. Pure; no Spring. */
public final class CatalogFormValidator {

    static final int MAX_FIELDS = 15;
    static final int TEXT_MAX = 200;
    static final int TEXTAREA_MAX = 2000;

    private CatalogFormValidator() {
    }

    /** Problems with the definition itself, reported as {@code fields[i].<property>}. */
    public static List<FieldProblem> checkDefinition(List<CatalogField> fields) {
        var problems = new ArrayList<FieldProblem>();
        if (fields.size() > MAX_FIELDS) {
            problems.add(new FieldProblem("fields", "at most " + MAX_FIELDS + " fields"));
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            CatalogField field = fields.get(i);
            if (!keys.add(field.key())) {
                problems.add(new FieldProblem("fields[" + i + "].key", "is used by another field"));
            }
            if (field.type() == CatalogFieldType.SELECT && field.options().isEmpty()) {
                problems.add(new FieldProblem("fields[" + i + "].options", "a choice field needs options"));
            }
            if (field.type() == CatalogFieldType.SELECT && new HashSet<>(field.options()).size() != field.options().size()) {
                problems.add(new FieldProblem("fields[" + i + "].options", "options must be different"));
            }
            if (field.type() != CatalogFieldType.SELECT && !field.options().isEmpty()) {
                problems.add(new FieldProblem("fields[" + i + "].options", "only choice fields have options"));
            }
        }
        return problems;
    }

    /** The answers to keep (trimmed, in field order, unknown keys and blanks dropped), or the problems found. */
    public static Result checkAnswers(List<CatalogField> fields, Map<String, String> answers) {
        Map<String, String> given = answers == null ? Map.of() : answers;
        var problems = new ArrayList<FieldProblem>();
        var kept = new LinkedHashMap<String, String>();
        for (CatalogField field : fields) {
            String raw = given.get(field.key());
            String value = raw == null ? "" : raw.strip();
            String path = "answers." + field.key();
            if (value.isEmpty()) {
                if (field.required()) {
                    problems.add(new FieldProblem(path, "is required"));
                }
                continue;
            }
            String problem = switch (field.type()) {
                case TEXT -> value.length() > TEXT_MAX ? "at most " + TEXT_MAX + " characters" : null;
                case TEXTAREA -> value.length() > TEXTAREA_MAX ? "at most " + TEXTAREA_MAX + " characters" : null;
                case NUMBER -> isNumber(value) ? null : "must be a number";
                case SELECT -> field.options().contains(value) ? null : "must be one of the options";
            };
            if (problem != null) {
                problems.add(new FieldProblem(path, problem));
            } else {
                kept.put(field.key(), value);
            }
        }
        return new Result(kept, problems);
    }

    private static boolean isNumber(String value) {
        if (value.length() > 18) {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public record Result(Map<String, String> answers, List<FieldProblem> problems) {

        public boolean valid() {
            return problems.isEmpty();
        }
    }
}
