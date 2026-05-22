package com.rookiefly.open.shardbatis.converter;

import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.piped.FromQuery;

public class SelectSqlConverter extends AbstractSqlConverter {

    @Override
    protected Statement doConvert(Statement statement, final Object params,
                                  final String mapperId) {
        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException(
                    "The argument statement must is instance of Select.");
        }
        TableNameModifier modifier = new TableNameModifier(params, mapperId);
        Select select = (Select) statement;
        select.accept((SelectVisitor<Void>) modifier, null);
        return statement;
    }

    private class TableNameModifier extends ExpressionVisitorAdapter<Void>
            implements SelectVisitor<Void>, FromItemVisitor<Void> {

        private final Object params;
        private final String mapperId;

        TableNameModifier(Object params, String mapperId) {
            this.params = params;
            this.mapperId = mapperId;
            this.setSelectVisitor(this);
        }

        // Resolve conflicting no-arg defaults from both interfaces
        @Override public void visit(PlainSelect ps) { visit(ps, null); }
        @Override public void visit(SetOperationList sol) { visit(sol, null); }
        @Override public void visit(ParenthesedSelect ps) { visit(ps, null); }
        @Override public void visit(Values v) { visit(v, null); }
        @Override public void visit(LateralSubSelect l) { visit(l, null); }
        @Override public void visit(TableStatement ts) { visit(ts, null); }

        // ========== ExpressionVisitor: handle ALL/ANY subqueries ==========
        @Override
        public <S> Void visit(AnyComparisonExpression expr, S context) {
            expr.getSelect().accept((SelectVisitor<Void>) this, context);
            return null;
        }

        // ========== Shared SelectVisitor/FromItemVisitor methods ==========

        @Override
        public <S> Void visit(PlainSelect plainSelect, S context) {
            if (plainSelect.getFromItem() != null) {
                plainSelect.getFromItem().accept(this, context);
            }
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    join.getFromItem().accept(this, context);
                }
            }
            if (plainSelect.getWhere() != null) {
                plainSelect.getWhere().accept(this, context);
            }
            return null;
        }

        @Override
        public <S> Void visit(SetOperationList setOpList, S context) {
            for (Select sel : setOpList.getSelects()) {
                sel.accept((SelectVisitor<Void>) this, context);
            }
            return null;
        }

        @Override
        public <S> Void visit(ParenthesedSelect parenthesedSelect, S context) {
            parenthesedSelect.getSelect().accept((SelectVisitor<Void>) this, context);
            return null;
        }

        @Override
        public <S> Void visit(Values values, S context) { return null; }
        @Override
        public <S> Void visit(LateralSubSelect lss, S context) { return null; }
        @Override
        public <S> Void visit(TableStatement ts, S context) { return null; }
        @Override
        public <S> Void visit(FromQuery fq, S context) { return null; }

        // ========== SelectVisitor only ==========
        @Override
        public <S> Void visit(WithItem<?> wi, S context) { return null; }

        // ========== FromItemVisitor only ==========
        @Override
        public <S> Void visit(Table tableName, S context) {
            String table = tableName.getName();
            table = convertTableName(table, params, mapperId);
            tableName.setName(table);
            return null;
        }

        @Override
        public <S> Void visit(ParenthesedFromItem pfi, S context) {
            pfi.getFromItem().accept(this, context);
            if (pfi.getJoins() != null) {
                for (Join join : pfi.getJoins()) {
                    join.getFromItem().accept(this, context);
                }
            }
            return null;
        }

        @Override
        public <S> Void visit(TableFunction tf, S context) { return null; }
    }
}
